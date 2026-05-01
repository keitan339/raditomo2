package com.raditomo.batch;

import com.raditomo.AbstractIT;
import com.raditomo.batch.entity.BatchExecution;
import com.raditomo.batch.entity.BatchStatus;
import com.raditomo.batch.entity.BatchType;
import com.raditomo.batch.entity.TriggeredBy;
import com.raditomo.batch.repository.BatchExecutionRepository;
import com.raditomo.batch.service.F2BatchRunner;
import com.raditomo.batch.service.F4BatchRunner;
import com.raditomo.batch.service.TimefreeDownloadService;
import com.raditomo.notification.service.NotificationService;
import com.raditomo.program.service.ProgramFetchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

/**
 * F4 → F2 連鎖の両経路（event 経由 / 同期）を実 Spring コンテキストで検証する。
 *
 * フレームワーク境界の挙動を確認するための IT:
 *   1. {@code @TransactionalEventListener(fallbackExecution=true)} が Tx 外 publish でも発火する
 *   2. {@code @Async} 経由で F2 が走り、batch_execution が完了する
 *   3. F4 サマリ + F2 サマリが combined summary として DB に書かれる（detached entity setSummary では書けない経路）
 *   4. CLI 同期経路 ({@link F4BatchRunner#runF4F2Synchronously}) が同一スレッドで両フェーズを完了する
 *
 * 重い依存（ラジコ API / ffmpeg / SMTP）はモック差し替え。
 */
class F4F2ChainIT extends AbstractIT {

    @Autowired F4BatchRunner f4BatchRunner;
    @Autowired BatchExecutionRepository batchExecutionRepository;

    // 重い外部 I/O は MockitoBean で差し替え
    @MockitoBean ProgramFetchService programFetchService;
    @MockitoBean TimefreeDownloadService timefreeDownloadService;
    @MockitoBean NotificationService notificationService;

    @Autowired F2BatchRunner f2BatchRunner; // not strictly required but verify wiring

    @BeforeEach
    void resetBatchRows() {
        // 連鎖排他チェックを邪魔する RUNNING を消す
        batchExecutionRepository.deleteAll();
        // F4 1 回呼ぶたびに 1 局 5 番組取得した体にする
        when(programFetchService.fetchAndPersist(any(), any()))
                .thenReturn(new ProgramFetchService.FetchAndPersistResult(1, 5, 0));
        // F2 は候補 3 件、全部成功とみなす
        when(timefreeDownloadService.processAll(anyList()))
                .thenReturn(new TimefreeDownloadService.DownloadSummary(3, 0, 0));
    }

    /**
     * Web/スケジューラ経路: {@code runF4(F4_F2, ...)} は F4 完了後 event を publish し、
     * @TransactionalEventListener(fallbackExecution=true) + @Async で F2 が別スレッドで走る。
     *
     * 期待:
     * - batch_execution は最終的に SUCCESS
     * - summary に F4 部 ("F4 areas=...") と F2 部 ("F2 success=...") の両方が入る
     * - TimefreeDownloadService.processAll が 1 度以上呼ばれる
     */
    @Test
    void scheduledPath_publishesEventOutsideTx_andF2RunsAsync() {
        BatchExecution exec = f4BatchRunner.runF4(
                BatchType.F4_F2, TriggeredBy.SCHEDULER, null, null);

        // @Async リスナーの完了を最大 10 秒待つ
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            BatchExecution latest = batchExecutionRepository.findById(exec.getId()).orElseThrow();
            assertThat(latest.getStatus()).isEqualTo(BatchStatus.SUCCESS);
            assertThat(latest.getSummary()).contains("F4 areas=");
            assertThat(latest.getSummary()).contains("F2 success=3");
        });

        verify(timefreeDownloadService, atLeastOnce()).processAll(anyList());
        verify(notificationService, atLeastOnce()).notifyAllPendingFailures();
    }

    /**
     * CLI 経路: {@code runF4F2Synchronously} は event を経由せず同一スレッドで完了する。
     *
     * 期待:
     * - 戻り値が SUCCESS（呼び出しから return した時点で完了している）
     * - summary に F4 部 + F2 部
     * - 待ち時間ゼロで検査可能
     */
    @Test
    void cliPath_runsBothPhasesSynchronously() {
        BatchExecution result = f4BatchRunner.runF4F2Synchronously(
                TriggeredBy.CLI, null, null);

        assertThat(result.getStatus()).isEqualTo(BatchStatus.SUCCESS);
        assertThat(result.getSummary()).contains("F4 areas=");
        assertThat(result.getSummary()).contains("F2 success=3");

        // DB にも反映されている（complete() を経由している証拠）
        BatchExecution persisted = batchExecutionRepository.findById(result.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(BatchStatus.SUCCESS);
        assertThat(persisted.getSummary()).isEqualTo(result.getSummary());

        verify(timefreeDownloadService, atLeastOnce()).processAll(anyList());
    }

    /**
     * F4 単独 ({@code runF4(F4, ...)}) では F2 が走らない。
     * event は publish されるが {@code chainToF2=false} なのでリスナーは早期 return。
     */
    @Test
    void f4Only_doesNotChainToF2() {
        BatchExecution result = f4BatchRunner.runF4(
                BatchType.F4, TriggeredBy.WEB, null, null);

        assertThat(result.getStatus()).isEqualTo(BatchStatus.SUCCESS);

        // 念のため非同期リスナーが誤発火しないことを少し待ってから検査
        await().pollDelay(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    verify(timefreeDownloadService, org.mockito.Mockito.never()).processAll(anyList());
                });
    }

    /**
     * F2 が PARTIAL_FAILURE のとき batch_execution も PARTIAL_FAILURE になる。
     * combined summary に両フェーズの数字が出る。
     */
    @Test
    void f2PartialFailure_propagatesToBatchStatus() {
        when(timefreeDownloadService.processAll(anyList()))
                .thenReturn(new TimefreeDownloadService.DownloadSummary(2, 1, 0));

        BatchExecution result = f4BatchRunner.runF4F2Synchronously(
                TriggeredBy.CLI, null, null);

        assertThat(result.getStatus()).isEqualTo(BatchStatus.PARTIAL_FAILURE);
        assertThat(result.getSummary()).contains("F2 success=2 failed=1");
    }
}
