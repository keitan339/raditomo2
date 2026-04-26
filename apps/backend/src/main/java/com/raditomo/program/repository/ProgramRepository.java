package com.raditomo.program.repository;

import com.raditomo.program.entity.Program;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ProgramRepository extends JpaRepository<Program, Long> {
    List<Program> findByBroadcastDateAndStationIdOrderByBroadcastStartAtAsc(
            LocalDate broadcastDate, String stationId);

    Optional<Program> findByStationIdAndBroadcastStartAt(String stationId, OffsetDateTime broadcastStartAt);
}
