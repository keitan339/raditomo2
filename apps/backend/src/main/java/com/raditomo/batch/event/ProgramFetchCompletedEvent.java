package com.raditomo.batch.event;

import com.raditomo.batch.entity.TriggeredBy;

/**
 * F4（番組表取得）完了通知イベント。
 *
 * - F4_F2 経由の場合は F2 リスナーが続行して走る
 * - F4 単体（cli download-programs / API type=F4）の場合は連鎖しない
 *
 * F2 は @TransactionalEventListener(phase = AFTER_COMMIT) で受信する。
 */
public record ProgramFetchCompletedEvent(
        Long batchExecutionId,
        TriggeredBy triggeredBy,
        boolean chainToF2,
        String optionsJson,
        String f4Summary
) {
}
