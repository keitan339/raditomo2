package com.raditomo.history.dto;

import java.time.OffsetDateTime;

/**
 * GROUP BY クエリ用の射影 DTO。
 */
public record RecordingGroupRow(String title, long count, OffsetDateTime latestBroadcastAt) {
}
