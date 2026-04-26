package com.raditomo.history.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.raditomo.history.entity.DownloadHistory;
import com.raditomo.history.entity.DownloadStatus;

import java.time.OffsetDateTime;
import java.util.List;

public class HistoryDtos {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HistoryItem(
            Long id,
            String stationId,
            String programTitle,
            String performers,
            OffsetDateTime broadcastStartAt,
            OffsetDateTime broadcastEndAt,
            DownloadStatus status,
            String errorMessage,
            OffsetDateTime attemptedAt,
            OffsetDateTime fileDeletedAt
    ) {
        public static HistoryItem from(DownloadHistory h) {
            return new HistoryItem(
                    h.getId(), h.getStationId(), h.getProgramTitle(), h.getPerformers(),
                    h.getBroadcastStartAt(), h.getBroadcastEndAt(), h.getStatus(),
                    h.getErrorMessage(), h.getAttemptedAt(), h.getFileDeletedAt());
        }
    }

    public record PageResponse<T>(
            List<T> content,
            int page, int size,
            long totalElements, int totalPages) {}

    private HistoryDtos() {}
}
