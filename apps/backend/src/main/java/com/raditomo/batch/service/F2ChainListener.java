package com.raditomo.batch.service;

import com.raditomo.batch.entity.BatchExecution;
import com.raditomo.batch.entity.BatchStatus;
import com.raditomo.batch.event.ProgramFetchCompletedEvent;
import com.raditomo.batch.repository.BatchExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * F4 完了 → F2 連鎖。
 *
 * - {@link ProgramFetchCompletedEvent#chainToF2()} が true のときのみ動作
 * - F4 の Tx がコミットされてから動く（{@code AFTER_COMMIT}）
 * - 非同期実行（{@code @Async}）。リクエスト/スケジューラを長時間ブロックしない
 * - F2 は新しい batch_execution を作らず、F4_F2 の execution に書き込んで complete する
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class F2ChainListener {

    private final F2BatchRunner f2BatchRunner;
    private final BatchExecutionService batchExecutionService;
    private final BatchExecutionRepository batchExecutionRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProgramFetchCompleted(ProgramFetchCompletedEvent event) {
        if (!event.chainToF2()) {
            log.debug("F4 finished without chain (executionId={})", event.batchExecutionId());
            return;
        }
        log.info("F4 → F2 chain firing: executionId={}", event.batchExecutionId());
        try {
            TimefreeDownloadService.DownloadSummary summary =
                    f2BatchRunner.runForChain(event.optionsJson());
            BatchStatus finalStatus = summary.failed() == 0
                    ? BatchStatus.SUCCESS
                    : BatchStatus.PARTIAL_FAILURE;
            String existingSummary = batchExecutionRepository.findById(event.batchExecutionId())
                    .map(BatchExecution::getSummary)
                    .orElse("");
            String combined = (existingSummary == null ? "" : existingSummary + " | ")
                    + String.format("F2 success=%d failed=%d expired=%d",
                            summary.success(), summary.failed(), summary.expired());
            batchExecutionService.complete(event.batchExecutionId(), finalStatus, combined);
        } catch (RuntimeException e) {
            log.error("F2 chain execution failed: id={}", event.batchExecutionId(), e);
            batchExecutionService.complete(event.batchExecutionId(), BatchStatus.FAILED, e.toString());
        }
    }
}
