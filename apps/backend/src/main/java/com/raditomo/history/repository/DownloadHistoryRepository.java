package com.raditomo.history.repository;

import com.raditomo.history.entity.DownloadHistory;
import com.raditomo.history.entity.DownloadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface DownloadHistoryRepository extends JpaRepository<DownloadHistory, Long> {
    Page<DownloadHistory> findByUserIdAndStatus(Long userId, DownloadStatus status, Pageable pageable);

    long countByUserIdAndStatus(Long userId, DownloadStatus status);

    Optional<DownloadHistory> findFirstByUserIdAndStationIdAndBroadcastStartAtAndStatus(
            Long userId, String stationId, OffsetDateTime broadcastStartAt, DownloadStatus status);
}
