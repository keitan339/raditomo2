package com.raditomo.batch.service;

import com.raditomo.batch.entity.*;
import com.raditomo.batch.repository.BatchExecutionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * batch_executions テーブルの管理 + 排他制御。
 *
 * 排他ルール（設計 01 / 05）:
 * - F2 と F4_F2 は同時実行不可（同じテーブル領域への書き込み・ラジコへの過負荷を避けるため）
 * - F4 単体は F4 / F4_F2 と同時実行不可（番組表 upsert 衝突を避ける）
 *
 * トランザクション内で SELECT FOR UPDATE → 既存 RUNNING の存在確認 → なければ INSERT。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchExecutionService {

    private final BatchExecutionRepository repository;
    private final Clock clock;

    /**
     * RUNNING 行を作って返す。既存 RUNNING があれば BatchAlreadyRunningException。
     */
    @Transactional
    public BatchExecution start(BatchType type, TriggeredBy triggeredBy, Long triggeredUserId, String optionsJson) {
        List<BatchType> exclusive = exclusiveSetOf(type);
        List<BatchExecution> running = repository.findRunningWithLock(BatchStatus.RUNNING, exclusive);
        if (!running.isEmpty()) {
            throw new BatchAlreadyRunningException(running.get(0));
        }
        BatchExecution exec = BatchExecution.builder()
                .batchType(type)
                .triggeredBy(triggeredBy)
                .triggeredUserId(triggeredUserId)
                .status(BatchStatus.RUNNING)
                .options(optionsJson)
                .build();
        BatchExecution saved = repository.save(exec);
        log.info("Batch started: id={} type={} triggeredBy={}", saved.getId(), type, triggeredBy);
        return saved;
    }

    @Transactional
    public BatchExecution complete(Long executionId, BatchStatus finalStatus, String summary) {
        BatchExecution exec = repository.findById(executionId)
                .orElseThrow(() -> new IllegalStateException("BatchExecution not found: " + executionId));
        exec.setStatus(finalStatus);
        exec.setSummary(summary);
        exec.setFinishedAt(OffsetDateTime.now(clock).atZoneSameInstant(ZoneId.of("Asia/Tokyo")).toOffsetDateTime());
        BatchExecution saved = repository.save(exec);
        log.info("Batch finished: id={} type={} status={}", saved.getId(), saved.getBatchType(), finalStatus);
        return saved;
    }

    /** 同時実行を許さないバッチ集合。 */
    static List<BatchType> exclusiveSetOf(BatchType type) {
        return switch (type) {
            case F4 -> List.of(BatchType.F4, BatchType.F4_F2);
            case F2 -> List.of(BatchType.F2, BatchType.F4_F2);
            case F4_F2 -> List.of(BatchType.F4, BatchType.F2, BatchType.F4_F2);
        };
    }

    public static class BatchAlreadyRunningException extends RuntimeException {
        private final BatchExecution running;

        public BatchAlreadyRunningException(BatchExecution running) {
            super("Already running: id=" + running.getId() + " type=" + running.getBatchType());
            this.running = running;
        }

        public BatchExecution getRunning() { return running; }
    }
}
