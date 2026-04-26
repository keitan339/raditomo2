package com.raditomo.history.repository;

import com.raditomo.history.entity.DownloadHistory;
import com.raditomo.history.entity.DownloadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface DownloadHistoryRepository extends JpaRepository<DownloadHistory, Long> {
    Page<DownloadHistory> findByUserIdAndStatus(Long userId, DownloadStatus status, Pageable pageable);

    Page<DownloadHistory> findByUserId(Long userId, Pageable pageable);

    long countByUserIdAndStatus(Long userId, DownloadStatus status);

    Optional<DownloadHistory> findFirstByUserIdAndStationIdAndBroadcastStartAtAndStatus(
            Long userId, String stationId, OffsetDateTime broadcastStartAt, DownloadStatus status);

    List<DownloadHistory> findByUserIdAndProgramTitleOrderByBroadcastStartAtDesc(
            Long userId, String programTitle);

    /**
     * 番組名グルーピング表示用: タイトル別に件数と最新放送日時を集計。
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT new com.raditomo.history.dto.RecordingGroupRow(
                h.programTitle, COUNT(h), MAX(h.broadcastStartAt))
            FROM DownloadHistory h
            WHERE h.userId = :userId
              AND h.status = com.raditomo.history.entity.DownloadStatus.SUCCESS
              AND h.fileDeletedAt IS NULL
            GROUP BY h.programTitle
            ORDER BY MAX(h.broadcastStartAt) DESC
            """)
    List<com.raditomo.history.dto.RecordingGroupRow> findRecordingGroups(
            @org.springframework.data.repository.query.Param("userId") Long userId);
}
