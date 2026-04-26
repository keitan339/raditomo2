package com.raditomo.station.repository;

import com.raditomo.station.entity.Station;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StationRepository extends JpaRepository<Station, String> {
    List<Station> findByAreaIdOrderBySortOrderAsc(String areaId);
}
