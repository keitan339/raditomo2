package com.raditomo.history.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "playback_positions")
@IdClass(PlaybackPosition.Pk.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaybackPosition {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "download_history_id")
    private Long downloadHistoryId;

    @Column(name = "position_seconds", nullable = false)
    private int positionSeconds;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pk implements Serializable {
        private Long userId;
        private Long downloadHistoryId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(userId, pk.userId) && Objects.equals(downloadHistoryId, pk.downloadHistoryId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, downloadHistoryId);
        }
    }
}
