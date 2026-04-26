package com.raditomo.batch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raditomo.batch.entity.BatchExecution;
import com.raditomo.batch.entity.BatchStatus;
import com.raditomo.batch.entity.BatchType;
import com.raditomo.batch.entity.TriggeredBy;
import com.raditomo.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * F2 バッチ本体。
 *
 * options（JSON）の解釈:
 *   { "date": "20260424", "force": false }
 *
 * - date 指定時はその放送日のみが対象
 * - force=true なら履歴 SUCCESS チェックをスキップして再DL
 *
 * BatchExecutionService.start で排他制御。完了時は SUCCESS / PARTIAL_FAILURE / FAILED を記録。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class F2BatchRunner {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DownloadCandidateMatcher matcher;
    private final TimefreeDownloadService downloadService;
    private final BatchExecutionService batchExecutionService;
    private final NotificationService notificationService;

    /**
     * 単独 F2 実行用エントリ。F4_F2 の連鎖は {@link #runForChain(String)} を使う。
     */
    public BatchExecution runF2(TriggeredBy triggeredBy, Long triggeredUserId, String optionsJson) {
        BatchExecution exec = batchExecutionService.start(BatchType.F2, triggeredBy, triggeredUserId, optionsJson);
        return runInside(exec, optionsJson);
    }

    /**
     * 既に start 済みの BatchExecution を引き継いで F2 を走らせる（Web 経由 @Async 用）。
     */
    public BatchExecution runOnExisting(BatchExecution exec, String optionsJson) {
        return runInside(exec, optionsJson);
    }

    /**
     * F4_F2 の連鎖実行内から呼ぶ。BatchExecution は F4_F2 のものを再利用するため、
     * このメソッドは start/complete を呼ばずダウンロードのみ走らせて結果を返す。
     */
    public TimefreeDownloadService.DownloadSummary runForChain(String optionsJson) {
        Filter f = parseOptions(optionsJson);
        List<DownloadCandidate> candidates = matcher.findCandidates(f.targetDate, f.force);
        log.info("F2(chained) candidates: {}", candidates.size());
        TimefreeDownloadService.DownloadSummary summary = downloadService.processAll(candidates);
        notificationService.notifyAllPendingFailures();
        return summary;
    }

    private BatchExecution runInside(BatchExecution exec, String optionsJson) {
        try {
            Filter f = parseOptions(optionsJson);
            List<DownloadCandidate> candidates = matcher.findCandidates(f.targetDate, f.force);
            log.info("F2 candidates: {}", candidates.size());
            TimefreeDownloadService.DownloadSummary summary = downloadService.processAll(candidates);

            // 失敗・期限切れがあればまとめて通知
            notificationService.notifyAllPendingFailures();

            BatchStatus finalStatus = summary.failed() == 0 ? BatchStatus.SUCCESS : BatchStatus.PARTIAL_FAILURE;
            String summaryStr = String.format("success=%d failed=%d expired=%d total=%d",
                    summary.success(), summary.failed(), summary.expired(), summary.total());
            return batchExecutionService.complete(exec.getId(), finalStatus, summaryStr);
        } catch (RuntimeException e) {
            log.error("F2 execution failed: id={}", exec.getId(), e);
            batchExecutionService.complete(exec.getId(), BatchStatus.FAILED, e.toString());
            throw e;
        }
    }

    private Filter parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) return new Filter(null, false);
        try {
            JsonNode node = MAPPER.readTree(optionsJson);
            LocalDate date = node.hasNonNull("date")
                    ? LocalDate.parse(node.get("date").asText(), DATE_FMT)
                    : null;
            boolean force = node.hasNonNull("force") && node.get("force").asBoolean(false);
            return new Filter(date, force);
        } catch (Exception e) {
            log.warn("Failed to parse F2 options JSON: {}", optionsJson);
            return new Filter(null, false);
        }
    }

    private record Filter(LocalDate targetDate, boolean force) {}
}
