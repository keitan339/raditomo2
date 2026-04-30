package com.raditomo.batch.service;

import com.raditomo.batch.entity.BatchExecution;
import com.raditomo.batch.entity.BatchStatus;
import com.raditomo.batch.entity.BatchType;
import com.raditomo.batch.repository.BatchExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Spring Boot 起動時に「前回までで RUNNING 状態のままのバッチ」を FAILED に倒す。
 *
 * 通常、起動時点で RUNNING がある場合は前回プロセスがクラッシュ・kill・再デプロイ等で
 * 正常終了せずに JVM が落ちた状態。実際のジョブはもう動いていないが、status が RUNNING
 * のまま残っていると {@code BatchExecutionService#start} の重複起動チェックで弾かれてしまう。
 *
 * 起動時に強制で FAILED に倒すことで、ユーザーが次回バッチを起動できる状態にする。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StaleRunningBatchCleaner {

    private final BatchExecutionRepository batchExecutionRepository;
    private final Clock clock;

    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void cleanupOnStartup() {
        List<BatchExecution> stale = batchExecutionRepository
                .findByStatusAndBatchTypeIn(BatchStatus.RUNNING, List.of(BatchType.values()));
        if (stale.isEmpty()) return;
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (BatchExecution e : stale) {
            log.warn("Marking stale RUNNING batch as FAILED: id={} type={} startedAt={}",
                    e.getId(), e.getBatchType(), e.getStartedAt());
            e.setStatus(BatchStatus.FAILED);
            e.setFinishedAt(now);
            String prev = e.getSummary();
            String reason = "Interrupted by application restart";
            e.setSummary(prev == null || prev.isBlank() ? reason : prev + " | " + reason);
        }
        batchExecutionRepository.saveAll(stale);
        log.info("Cleaned up {} stale RUNNING batch(es) on startup", stale.size());
    }
}
