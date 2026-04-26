package com.raditomo.program.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "programs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Program {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_id", nullable = false, length = 32)
    private String stationId;

    @Column(name = "broadcast_start_at", nullable = false)
    private OffsetDateTime broadcastStartAt;

    @Column(name = "broadcast_end_at", nullable = false)
    private OffsetDateTime broadcastEndAt;

    @Column(name = "broadcast_date", nullable = false)
    private LocalDate broadcastDate;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String performers;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String info;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private OffsetDateTime updatedAt;
}
