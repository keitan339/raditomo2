package com.raditomo.program.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "raw_program_xmls")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RawProgramXml {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_id", nullable = false, length = 32)
    private String stationId;

    @Column(name = "broadcast_date", nullable = false)
    private LocalDate broadcastDate;

    @Column(name = "file_path", nullable = false, columnDefinition = "TEXT")
    private String filePath;

    @Column(name = "fetched_at", nullable = false, insertable = false)
    private OffsetDateTime fetchedAt;
}
