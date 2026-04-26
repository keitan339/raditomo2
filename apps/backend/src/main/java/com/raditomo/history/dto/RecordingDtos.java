package com.raditomo.history.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.raditomo.history.entity.DownloadHistory;

import java.time.OffsetDateTime;

public class RecordingDtos {

    public record RecordingGroupResponse(
            String title, long count, OffsetDateTime latestBroadcastAt) {
        public static RecordingGroupResponse from(RecordingGroupRow row) {
            return new RecordingGroupResponse(row.title(), row.count(), row.latestBroadcastAt());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecordingResponse(
            Long historyId,
            String stationId,
            String programTitle,
            String performers,
            OffsetDateTime broadcastStartAt,
            OffsetDateTime broadcastEndAt,
            Integer durationSeconds,
            Long fileSizeBytes,
            String mp3Path,
            String hlsUrl,
            boolean reDownloadable
    ) {
        public static RecordingResponse from(DownloadHistory h, String hlsUrl, boolean reDownloadable) {
            return new RecordingResponse(
                    h.getId(), h.getStationId(), h.getProgramTitle(), h.getPerformers(),
                    h.getBroadcastStartAt(), h.getBroadcastEndAt(),
                    h.getDurationSeconds(), h.getFileSizeBytes(),
                    h.getMp3Path(), hlsUrl, reDownloadable);
        }
    }

    public record PlaybackPositionResponse(int positionSeconds) {}

    public record PlaybackPositionUpdateRequest(int positionSeconds) {}

    private RecordingDtos() {}
}
