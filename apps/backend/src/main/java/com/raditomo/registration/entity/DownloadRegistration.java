package com.raditomo.registration.entity;

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

    @Column(name = "updated_at", nullable = false, insertable = false)
    private OffsetDateTime updatedAt;
}
