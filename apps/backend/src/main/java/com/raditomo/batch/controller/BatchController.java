package com.raditomo.batch.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raditomo.batch.dto.BatchDtos.BatchExecutionResponse;
import com.raditomo.batch.dto.BatchDtos.RunBatchRequest;
import com.raditomo.batch.dto.BatchDtos.RunBatchResponse;
import com.raditomo.batch.entity.BatchExecution;
import com.raditomo.batch.entity.BatchStatus;
import com.raditomo.batch.entity.BatchType;
import com.raditomo.batch.entity.TriggeredBy;
import com.raditomo.batch.repository.BatchExecutionRepository;
import com.raditomo.batch.service.BatchExecutionService;
import com.raditomo.batch.service.F2BatchRunner;
import com.raditomo.batch.service.F4BatchRunner;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * バッチ手動実行 API。
 *
 * - POST /api/batch/run         : F4 / F2 / F4_F2 を非同期起動
 * - GET  /api/batch/executions/{id} : 状態取得
 *
 * フロー:
 * 1. controller が同期で BatchExecutionService.start を呼ぶ（排他チェック）。衝突なら 409
 * 2. 取得した BatchExecution を非同期 dispatcher に渡し、本体を別スレッドで回す
 * 3. レスポンスは batchExecutionId と RUNNING を即時返す
 */
@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
@Slf4j
public class BatchController {

    private final BatchExecutionRepository batchExecutionRepository;
    private final BatchExecutionService batchExecutionService;
    private final BatchAsyncDispatcher dispatcher;

    @PostMapping("/run")
    public ResponseEntity<RunBatchResponse> run(
            @Valid @RequestBody RunBatchRequest request,
            @AuthenticationPrincipal Long userId) {

        String optionsJson = serializeOptions(request.options());
        try {
            BatchExecution exec = batchExecutionService.start(
                    request.type(), TriggeredBy.WEB, userId, optionsJson);
            dispatcher.runAsync(exec, request.type(), optionsJson);
            return ResponseEntity.ok(RunBatchResponse.running(exec));
        } catch (BatchExecutionService.BatchAlreadyRunningException e) {
            log.info("Batch run rejected (already running): id={}", e.getRunning().getId());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(RunBatchResponse.running(e.getRunning()));
        }
    }

    @GetMapping("/executions/{id}")
    public ResponseEntity<BatchExecutionResponse> getExecution(@PathVariable Long id) {
        return batchExecutionRepository.findById(id)
                .map(BatchExecutionResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 現在実行中の音声 DL バッチ（F2 単体 / F4 → F2 連鎖）を返す。
     * 設定画面・履歴画面のステータスバナー用。実行中で無ければ空配列。
     * バッチは同時 1 件しか走らない設計なので最大 1 件。
     */
    @GetMapping("/executions/running-download")
    public List<BatchExecutionResponse> getRunningDownload() {
        return batchExecutionRepository
                .findByStatusAndBatchTypeIn(BatchStatus.RUNNING, List.of(BatchType.F2, BatchType.F4_F2))
                .stream()
                .map(BatchExecutionResponse::from)
                .toList();
    }

    private String serializeOptions(Map<String, Object> options) {
        if (options == null || options.isEmpty()) return null;
        try {
            return new ObjectMapper().writeValueAsString(options);
        } catch (Exception e) {
            log.warn("Failed to serialize options: {}", options);
            return null;
        }
    }

    /**
     * 非同期実行用 Component。同一クラス内 @Async は self-invocation 経由だと有効化されないため別 Bean。
     */
    @Component
    @RequiredArgsConstructor
    @Slf4j
    public static class BatchAsyncDispatcher {

        private final F4BatchRunner f4BatchRunner;
        private final F2BatchRunner f2BatchRunner;
        private final BatchExecutionService batchExecutionService;

        @Async
        public void runAsync(BatchExecution exec, BatchType type, String optionsJson) {
            try {
                switch (type) {
                    case F4 -> f4BatchRunner.runOnExisting(exec, BatchType.F4, TriggeredBy.WEB, optionsJson);
                    case F4_F2 -> f4BatchRunner.runOnExisting(exec, BatchType.F4_F2, TriggeredBy.WEB, optionsJson);
                    case F2 -> f2BatchRunner.runOnExisting(exec, optionsJson);
                }
            } catch (BatchExecutionService.BatchAlreadyRunningException e) {
                log.warn("Async dispatch found another running batch: id={}", e.getRunning().getId());
            } catch (RuntimeException e) {
                log.error("Async batch failed: id={}", exec.getId(), e);
            }
        }
    }
}
