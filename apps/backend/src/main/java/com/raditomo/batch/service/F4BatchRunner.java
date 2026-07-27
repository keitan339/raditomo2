package com.raditomo.batch.service;

import com.raditomo.batch.entity.BatchExecution;
import com.raditomo.batch.entity.BatchStatus;
import com.raditomo.batch.entity.BatchType;
import com.raditomo.batch.entity.TriggeredBy;
import com.raditomo.batch.event.ProgramFetchCompletedEvent;
import com.raditomo.program.service.ProgramFetchService;
import com.raditomo.user.repository.UserSettingsRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * F4 バッチ本体。
 *
 * - 実行範囲: 過去7日 + 当日 + 未来7日 = 15日（デフォルト）
 * - 対象エリア: user_settings.current_area_id の distinct 集合。空ならデフォルト JP13
 * - 各 (date, areaId) ごとに ProgramFetchService.fetchAndPersist を呼ぶ
 * - 1局/1日が失敗しても他は進める（PARTIAL_FAILURE 扱い）
 * - 完了時に ProgramFetchCompletedEvent を発行
 *
 * BatchExecutionService.start で排他制御済みの BatchExecution を渡される前提。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class F4BatchRunner {

    private static final String DEFAULT_AREA = "JP13";
    private static final int DAYS_PAST = 7;
    private static final int DAYS_FUTURE = 7;

    private final ProgramFetchService programFetchService;
    private final UserSettingsRepository userSettingsRepository;
    private final BatchExecutionService batchExecutionService;
    private final F2BatchRunner f2BatchRunner;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * F4 / F4_F2 を実行する。BatchExecutionService.start 済みの execution が渡される。
     * 失敗時も execution は complete される。
     *
     * @param batchType   F4 単体なら F4、連鎖実行なら F4_F2
     * @param triggeredBy 起動元
     * @param triggeredUserId 起動ユーザー（任意）
     * @param optionsJson 任意のオプション JSON（CLI/Web 経由）
     */
    public BatchExecution runF4(BatchType batchType, TriggeredBy triggeredBy,
                                Long triggeredUserId, String optionsJson) {
        BatchExecution exec = batchExecutionService.start(batchType, triggeredBy, triggeredUserId, optionsJson);
        return runOnExisting(exec, batchType, triggeredBy, optionsJson);
    }

    /**
     * 既に start 済みの BatchExecution を引き継いで F4 を走らせる。
     * Web 経由（@Async）で start を同期に行ったあと、本体を非同期で回すために使う。
     */
    public BatchExecution runOnExisting(BatchExecution exec, BatchType batchType,
                                        TriggeredBy triggeredBy, String optionsJson) {
        try {
            F4PhaseResult result = runF4Phase();
            boolean chainToF2 = batchType == BatchType.F4_F2;

            if (chainToF2) {
                // F4_F2 のときは completion を F2 リスナーに委ねる。F4 結果は event に積んで渡す。
                eventPublisher.publishEvent(new ProgramFetchCompletedEvent(
                        exec.getId(), triggeredBy, true, optionsJson, result.summary()));
                return exec;
            }

            BatchStatus finalStatus = result.failedCombinations() == 0
                    ? BatchStatus.SUCCESS : BatchStatus.PARTIAL_FAILURE;
            BatchExecution completed = batchExecutionService.complete(exec.getId(), finalStatus, result.summary());
            eventPublisher.publishEvent(new ProgramFetchCompletedEvent(
                    completed.getId(), triggeredBy, false, optionsJson, result.summary()));
            return completed;
        } catch (RuntimeException e) {
            log.error("F4 execution failed: id={}", exec.getId(), e);
            batchExecutionService.complete(exec.getId(), BatchStatus.FAILED, e.toString());
            throw e;
        }
    }

    /**
     * F4 → F2 を同一スレッドで同期実行する。CLI ({@code raditomo download}) 用。
     *
     * 通常の Web/スケジューラ経路は F4 完了時に event を publish して F2 を @Async で連鎖させるが、
     * CLI モードは Picocli runnable が return するとすぐ JVM が exit するため、
     * @Async スレッドが処理途中で中断されてしまう。CLI では event を経由せず同期で F2 まで走らせる。
     */
    public BatchExecution runF4F2Synchronously(TriggeredBy triggeredBy, Long triggeredUserId, String optionsJson) {
        BatchExecution exec = batchExecutionService.start(
                BatchType.F4_F2, triggeredBy, triggeredUserId, optionsJson);
        try {
            F4PhaseResult result = runF4Phase();
            return f2BatchRunner.runForChainAndComplete(exec.getId(), optionsJson, result.summary());
        } catch (RuntimeException e) {
            log.error("F4_F2 sync execution failed: id={}", exec.getId(), e);
            batchExecutionService.complete(exec.getId(), BatchStatus.FAILED, e.toString());
            throw e;
        }
    }

    private F4PhaseResult runF4Phase() {
        int totalAreas = 0;
        int totalDays = 0;
        int totalPrograms = 0;
        int failedCombinations = 0;

        List<String> areaIds = resolveAreaIds();
        List<LocalDate> dates = dateRange();

        for (String areaId : areaIds) {
            totalAreas++;
            for (LocalDate date : dates) {
                totalDays++;
                try {
                    ProgramFetchService.FetchAndPersistResult r =
                            programFetchService.fetchAndPersist(date, areaId);
                    totalPrograms += r.programsUpserted();
                } catch (RuntimeException e) {
                    failedCombinations++;
                    log.warn("F4 partial failure: date={} area={} cause={}", date, areaId, e.toString(), e);
                }
            }
        }

        String summary = String.format("F4 areas=%d dates=%d programs=%d failedCombos=%d",
                totalAreas, totalDays, totalPrograms, failedCombinations);
        return new F4PhaseResult(totalAreas, totalDays, totalPrograms, failedCombinations, summary);
    }

    private List<String> resolveAreaIds() {
        // distinct current_area_id from user_settings
        @SuppressWarnings("unchecked")
        List<String> ids = entityManager
                .createQuery("SELECT DISTINCT us.currentAreaId FROM UserSettings us")
                .getResultList();
        Set<String> set = new HashSet<>(ids);
        if (set.isEmpty()) set.add(DEFAULT_AREA);
        return new ArrayList<>(set);
    }

    private List<LocalDate> dateRange() {
        // 5:00区切りの「今日」を基準に 7 日前〜7 日後
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Tokyo")));
        // ただし now が 05:00 未満なら前日扱い
        var nowJst = java.time.LocalTime.now(clock.withZone(ZoneId.of("Asia/Tokyo")));
        if (nowJst.isBefore(java.time.LocalTime.of(5, 0))) {
            today = today.minusDays(1);
        }
        List<LocalDate> dates = new ArrayList<>(DAYS_PAST + 1 + DAYS_FUTURE);
        for (int i = -DAYS_PAST; i <= DAYS_FUTURE; i++) {
            dates.add(today.plusDays(i));
        }
        return dates;
    }

    private record F4PhaseResult(int areas, int days, int programs, int failedCombinations, String summary) {}
}
