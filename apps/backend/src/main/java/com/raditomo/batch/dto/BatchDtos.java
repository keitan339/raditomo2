package com.raditomo.batch.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.raditomo.batch.entity.BatchExecution;
import com.raditomo.batch.entity.BatchStatus;
import com.raditomo.batch.entity.BatchType;
import com.raditomo.batch.entity.TriggeredBy;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.Map;

public class BatchDtos {

    public record RunBatchRequest(@NotNull BatchType type, Map<String, Object> options) {}

    public record RunBatchResponse(Long batchExecutionId, BatchStatus status) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BatchExecutionResponse(
            Long id,
            BatchType type,
            TriggeredBy triggeredBy,
            BatchStatus status,
            String summary,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt
    ) {
        public static BatchExecutionResponse from(BatchExecution e) {
            return new BatchExecutionResponse(
                    e.getId(), e.getBatchType(), e.getTriggeredBy(), e.getStatus(),
                    e.getSummary(), e.getStartedAt(), e.getFinishedAt());
        }
    }

    private BatchDtos() {}
}
