package com.raditomo.batch.service;

import com.raditomo.common.time.JstTimes;
import com.raditomo.history.entity.DownloadStatus;
import com.raditomo.history.repository.DownloadHistoryRepository;
import com.raditomo.program.entity.Program;
import com.raditomo.program.repository.ProgramRepository;
import com.raditomo.registration.entity.DownloadRegistration;
import com.raditomo.registration.entity.RegistrationStatus;
import com.raditomo.registration.entity.RegistrationType;
import com.raditomo.registration.repository.DownloadRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * F2 動的照合: 登録 × 番組表 × 履歴 → ダウンロード対象を導出する。
 *
 * 設計参照: docs/requirements/01-functional-requirements.md F2、docs/design/01-database-design.md
 *
 * 共通条件:
 * - 放送が完了している（broadcast_end_at <= now）
 * - 履歴に SUCCESS がない（force=true なら無視）
 * - 登録が ACTIVE
 *
 * ONCE:
 * - station_id + broadcast_start_at で番組を引き、上記条件を満たすなら候補
 *
 * WEEKLY 4 ステップ:
 * 1. 登録時の (station_id, broadcast_start_at) の番組を引き、title が一致 → 候補
 * 2. 一致しない場合、同じ放送日（5:00区切り）で同 title の番組を引く → 候補（時刻はその番組）
 * 3. それでも見つからない場合、登録時の時刻で「DB 上の該当時間帯の番組名」を使って候補化
 *    （別番組・特番でも必ず DL する設計判断。`project_weekly_matching.md` 参照）
 *
 * date 指定時は broadcast_date でフィルタ。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DownloadCandidateMatcher {

    private final DownloadRegistrationRepository registrationRepository;
    private final ProgramRepository programRepository;
    private final DownloadHistoryRepository historyRepository;
    private final Clock clock;

    /**
     * @param targetDate 放送日でフィルタする場合は指定。null なら全範囲
     * @param force      true なら履歴 SUCCESS チェックをスキップ
     */
    public List<DownloadCandidate> findCandidates(LocalDate targetDate, boolean force) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<DownloadRegistration> active = registrationRepository.findByStatus(RegistrationStatus.ACTIVE);
        return matchAll(active, targetDate, force, now);
    }

    /** package-private: テストから直接登録リストを渡せるようにする。 */
    List<DownloadCandidate> matchAll(List<DownloadRegistration> registrations,
                                     LocalDate targetDate,
                                     boolean force,
                                     OffsetDateTime now) {
        List<DownloadCandidate> candidates = new ArrayList<>();
        for (DownloadRegistration r : registrations) {
            try {
                Optional<DownloadCandidate> c = match(r, targetDate, force, now);
                c.ifPresent(candidates::add);
            } catch (RuntimeException e) {
                log.warn("Match failed for registration id={}: {}", r.getId(), e.toString());
            }
        }
        return candidates;
    }

    private Optional<DownloadCandidate> match(DownloadRegistration r, LocalDate targetDate,
                                              boolean force, OffsetDateTime now) {
        if (r.getRegistrationType() == RegistrationType.ONCE) {
            return matchOnce(r, targetDate, force, now);
        }
        return matchWeekly(r, targetDate, force, now);
    }

    private Optional<DownloadCandidate> matchOnce(DownloadRegistration r, LocalDate targetDate,
                                                  boolean force, OffsetDateTime now) {
        Optional<Program> program = programRepository.findByStationIdAndBroadcastStartAt(
                r.getStationId(), r.getBroadcastStartAt());
        if (program.isEmpty()) return Optional.empty();
        Program p = program.get();
        if (!matchesTargetDate(p.getBroadcastDate(), targetDate)) return Optional.empty();
        if (!isFinishedAndAvailable(p, now, force, r.getUserId())) return Optional.empty();
        return Optional.of(new DownloadCandidate(
                r.getId(), r.getUserId(), p.getStationId(),
                p.getTitle(), p.getPerformers(),
                p.getBroadcastStartAt(), p.getBroadcastEndAt(),
                RegistrationType.ONCE, "once"));
    }

    private Optional<DownloadCandidate> matchWeekly(DownloadRegistration r, LocalDate targetDate,
                                                    boolean force, OffsetDateTime now) {
        // 手順1+2: 登録時の時刻で title 一致するか
        Optional<Program> exact = programRepository.findByStationIdAndBroadcastStartAt(
                r.getStationId(), r.getBroadcastStartAt());
        if (exact.isPresent() && r.getTitle().equals(exact.get().getTitle())) {
            Program p = exact.get();
            if (matchesTargetDate(p.getBroadcastDate(), targetDate)
                    && isFinishedAndAvailable(p, now, force, r.getUserId())) {
                return Optional.of(toCandidate(r, p, "weekly-1"));
            }
        }

        // 手順3: 同じ放送日で同タイトル
        LocalDate broadcastDateToTry = targetDate != null
                ? targetDate
                : JstTimes.broadcastDate(r.getBroadcastStartAt());
        Optional<Program> sameDateSameTitle = programRepository
                .findFirstByStationIdAndBroadcastDateAndTitleOrderByBroadcastStartAtAsc(
                        r.getStationId(), broadcastDateToTry, r.getTitle());
        if (sameDateSameTitle.isPresent()) {
            Program p = sameDateSameTitle.get();
            if (isFinishedAndAvailable(p, now, force, r.getUserId())) {
                return Optional.of(toCandidate(r, p, "weekly-3"));
            }
        }

        // 手順4: 登録時刻で強制DL（DB 上の該当時間帯の番組名で履歴記録）
        // exact が存在すれば（title が違っても）その番組情報を使う。
        // exact が存在しない場合は、登録時の時刻・番組名そのまま。
        if (exact.isPresent()) {
            Program p = exact.get();
            if (matchesTargetDate(p.getBroadcastDate(), targetDate)
                    && isFinishedAndAvailable(p, now, force, r.getUserId())) {
                return Optional.of(toCandidate(r, p, "weekly-4"));
            }
        } else {
            // 番組表に該当時間帯の番組がない → 登録時の情報で強制DL
            if (!matchesTargetDate(JstTimes.broadcastDate(r.getBroadcastStartAt()), targetDate)) {
                return Optional.empty();
            }
            if (!r.getBroadcastEndAt().isBefore(now)) return Optional.empty();
            if (!force && hasSuccess(r.getUserId(), r.getStationId(), r.getBroadcastStartAt())) {
                return Optional.empty();
            }
            return Optional.of(new DownloadCandidate(
                    r.getId(), r.getUserId(), r.getStationId(),
                    r.getTitle(), null,
                    r.getBroadcastStartAt(), r.getBroadcastEndAt(),
                    RegistrationType.WEEKLY, "weekly-4"));
        }

        return Optional.empty();
    }

    private DownloadCandidate toCandidate(DownloadRegistration r, Program p, String step) {
        return new DownloadCandidate(
                r.getId(), r.getUserId(), p.getStationId(),
                p.getTitle(), p.getPerformers(),
                p.getBroadcastStartAt(), p.getBroadcastEndAt(),
                RegistrationType.WEEKLY, step);
    }

    private boolean matchesTargetDate(LocalDate broadcastDate, LocalDate targetDate) {
        return targetDate == null || broadcastDate.equals(targetDate);
    }

    private boolean isFinishedAndAvailable(Program p, OffsetDateTime now, boolean force, Long userId) {
        if (!p.getBroadcastEndAt().isBefore(now) && !p.getBroadcastEndAt().isEqual(now)) return false;
        if (!force && hasSuccess(userId, p.getStationId(), p.getBroadcastStartAt())) return false;
        return true;
    }

    private boolean hasSuccess(Long userId, String stationId, OffsetDateTime startAt) {
        return historyRepository.findFirstByUserIdAndStationIdAndBroadcastStartAtAndStatus(
                userId, stationId, startAt, DownloadStatus.SUCCESS).isPresent();
    }
}
