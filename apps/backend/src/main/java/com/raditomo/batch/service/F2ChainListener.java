package com.raditomo.batch.service;

import com.raditomo.batch.entity.BatchStatus;
import com.raditomo.batch.event.ProgramFetchCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * F4 完了 → F2 連鎖（Web / スケジューラ経路）。
 *
 * - {@link ProgramFetchCompletedEvent#chainToF2()} が true のときのみ動作
 * - F4 の Tx がコミットされてから動く（{@code AFTER_COMMIT}）
 * - {@code fallbackExecution=true}: スケジューラ等から Tx 外で publish された場合も発火させる
 *   （これがないとイベントが黙って破棄される）
 * - 非同期実行（{@code @Async}）。リクエスト/スケジューラを長時間ブロックしない
 * - F2 は新しい batch_execution を作らず、F4_F2 の execution に書き込んで complete する
 *
 * CLI ({@code raditomo download}) は JVM がすぐ終了して @Async が中断されるため、
 * このリスナー経由ではなく {@link F4BatchRunner#runF4F2Synchronously} を使う。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class F2ChainListener {

    private final F2BatchRunner f2BatchRunner;
    private final BatchExecutionService batchExecutionService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProgramFetchCompleted(ProgramFetchCompletedEvent event) {
        if (!event.chainToF2()) {
            log.debug("F4 finished without chain (executionId={})", event.batchExecutionId());
            return;
        }
        log.info("F4 → F2 chain firing: executionId={}", event.batchExecutionId());
        try {
            f2BatchRunner.runForChainAndComplete(
                    event.batchExecutionId(), event.optionsJson(), event.f4Summary());
        } catch (RuntimeException e) {
            log.error("F2 chain execution failed: id={}", event.batchExecutionId(), e);
            batchExecutionService.complete(event.batchExecutionId(), BatchStatus.FAILED, e.toString());
        }
    }
}
