package com.raditomo.user.repository;

import com.raditomo.user.entity.UserStationVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserStationVisibilityRepository
        extends JpaRepository<UserStationVisibility, UserStationVisibility.Pk> {

    @Query("""
            SELECT v FROM UserStationVisibility v
            JOIN com.raditomo.station.entity.Station s ON s.id = v.stationId
            WHERE v.userId = :userId AND s.areaId = :areaId
            """)
    List<UserStationVisibility> findByUserIdAndAreaId(
            @Param("userId") Long userId, @Param("areaId") String areaId);
}
