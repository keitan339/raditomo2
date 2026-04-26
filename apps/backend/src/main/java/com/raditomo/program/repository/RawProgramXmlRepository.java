package com.raditomo.program.repository;

import com.raditomo.program.entity.RawProgramXml;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface RawProgramXmlRepository extends JpaRepository<RawProgramXml, Long> {
    Optional<RawProgramXml> findByStationIdAndBroadcastDate(String stationId, LocalDate broadcastDate);
}
