package com.raditomo.station.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "stations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Station {
    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "area_id", nullable = false, length = 8)
    private String areaId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "ascii_name", length = 100)
    private String asciiName;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
