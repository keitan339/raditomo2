package com.raditomo.area.repository;

import com.raditomo.area.entity.Area;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AreaRepository extends JpaRepository<Area, String> {
    List<Area> findAllByOrderBySortOrderAsc();
}
