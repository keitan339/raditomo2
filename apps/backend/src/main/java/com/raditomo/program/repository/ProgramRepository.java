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

    /**
     * 毎週マッチング 手順3用: 同一放送局・同一放送日（5:00区切り）・同一タイトル。
     */
    Optional<Program> findFirstByStationIdAndBroadcastDateAndTitleOrderByBroadcastStartAtAsc(
            String stationId, LocalDate broadcastDate, String title);
}
