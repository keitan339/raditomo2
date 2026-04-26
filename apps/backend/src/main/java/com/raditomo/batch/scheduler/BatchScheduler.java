package com.raditomo.batch.scheduler;

import com.raditomo.batch.entity.BatchType;
import com.raditomo.batch.entity.TriggeredBy;
import com.raditomo.batch.service.BatchExecutionService;
import com.raditomo.batch.service.F4BatchRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 毎朝 5:30 JST に F4_F2（番組表取得 → DL 連鎖）を起動する。
 *
 * cron: `0 30 5 * * *` Asia/Tokyo
 *
 * 環境変数 `RADITOMO_SCHEDULER_ENABLED=false` で無効化可能（テスト用）。
 */
@Component
@ConditionalOnProperty(name = "raditomo.scheduler.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class BatchScheduler {

    private final F4BatchRunner f4BatchRunner;

    @Scheduled(cron = "${raditomo.scheduler.cron:0 30 5 * * *}", zone = "Asia/Tokyo")
    public void daily() {
        log.info("Scheduler firing daily F4_F2 batch");
        try {
            f4BatchRunner.runF4(BatchType.F4_F2, TriggeredBy.SCHEDULER, null, null);
        } catch (BatchExecutionService.BatchAlreadyRunningException e) {
            log.warn("Daily batch skipped: another batch is already running (id={})",
                    e.getRunning().getId());
        } catch (RuntimeException e) {
            log.error("Daily batch failed", e);
        }
    }
}
