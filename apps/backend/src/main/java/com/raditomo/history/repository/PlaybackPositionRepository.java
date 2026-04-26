package com.raditomo.history.repository;

import com.raditomo.history.entity.PlaybackPosition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaybackPositionRepository
        extends JpaRepository<PlaybackPosition, PlaybackPosition.Pk> {
}
