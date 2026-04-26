package com.raditomo.history.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "download_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DownloadHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "registration_id")
    private Long registrationId;

    @Column(name = "station_id", nullable = false, length = 32)
    private String stationId;

    @Column(name = "program_title", nullable = false, length = 500)
    private String programTitle;

    @Column(columnDefinition = "TEXT")
    private String performers;

    @Column(name = "broadcast_start_at", nullable = false)
    private OffsetDateTime broadcastStartAt;

    @Column(name = "broadcast_end_at", nullable = false)
    private OffsetDateTime broadcastEndAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DownloadStatus status;

    @Column(name = "mp3_path", columnDefinition = "TEXT")
    private String mp3Path;

    @Column(name = "hls_path", columnDefinition = "TEXT")
    private String hlsPath;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private short retryCount;

    @Column(name = "attempted_at", nullable = false, insertable = false)
    private OffsetDateTime attemptedAt;

    @Column(name = "notified_at")
    private OffsetDateTime notifiedAt;

    @Column(name = "file_deleted_at")
    private OffsetDateTime fileDeletedAt;
}
