package com.raditomo.registration.entity;

import com.raditomo.common.time.JstTimes;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "download_registrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DownloadRegistration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "station_id", nullable = false, length = 32)
    private String stationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_type", nullable = false, length = 16)
    private RegistrationType registrationType;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "broadcast_start_at", nullable = false)
    private OffsetDateTime broadcastStartAt;

    @Column(name = "broadcast_end_at", nullable = false)
    private OffsetDateTime broadcastEndAt;

    @Column(name = "day_of_week")
    private Short dayOfWeek;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RegistrationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    // insertable = false にすると UPDATE 時に値をセットし忘れて NULL 違反になるため、
    // アプリ側（@PrePersist / @PreUpdate）で必ず値を入れる。
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = OffsetDateTime.now(JstTimes.JST);
    }
}
