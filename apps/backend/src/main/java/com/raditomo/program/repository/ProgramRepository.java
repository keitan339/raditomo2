package com.raditomo.program.repository;

import com.raditomo.program.entity.Program;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * エリア・日付指定で全番組を放送局順・放送開始順で取得。
     */
    @Query("""
            SELECT p FROM Program p
            JOIN com.raditomo.station.entity.Station s ON s.id = p.stationId
            WHERE s.areaId = :areaId AND p.broadcastDate = :date
            ORDER BY s.sortOrder ASC, p.broadcastStartAt ASC
            """)
    List<Program> findByAreaAndDate(@Param("areaId") String areaId, @Param("date") LocalDate date);

    /**
     * タイトル / 出演者の部分一致検索。
     */
    @Query("""
            SELECT p FROM Program p
            JOIN com.raditomo.station.entity.Station s ON s.id = p.stationId
            WHERE s.areaId = :areaId
              AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(p.performers, '')) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY p.broadcastStartAt ASC
            """)
    List<Program> searchByAreaAndKeyword(
            @Param("areaId") String areaId, @Param("q") String q, Pageable pageable);
}
