package com.raditomo.history.repository;

import com.raditomo.history.entity.DownloadHistory;
import com.raditomo.history.entity.DownloadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface UnnotifiedHistoryRepository extends JpaRepository<DownloadHistory, Long> {

    @Query("""
            SELECT h FROM DownloadHistory h
            WHERE h.userId = :userId
              AND h.status IN :statuses
              AND h.notifiedAt IS NULL
            ORDER BY h.attemptedAt DESC
            """)
    List<DownloadHistory> findUnnotifiedForUser(
            @Param("userId") Long userId,
            @Param("statuses") List<DownloadStatus> statuses);

    @Modifying
    @Query("""
            UPDATE DownloadHistory h
            SET h.notifiedAt = :now
            WHERE h.id IN :ids
            """)
    int markNotified(@Param("ids") List<Long> ids, @Param("now") OffsetDateTime now);
}
