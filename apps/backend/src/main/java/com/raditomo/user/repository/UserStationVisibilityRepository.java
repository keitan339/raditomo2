package com.raditomo.user.repository;

import com.raditomo.user.entity.UserStationVisibility;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStationVisibilityRepository
        extends JpaRepository<UserStationVisibility, UserStationVisibility.Pk> {
}
