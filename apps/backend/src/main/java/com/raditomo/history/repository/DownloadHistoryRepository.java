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

    /** 未通知（バッジ未確認）の件数。履歴ページを開いて既読化されると 0 になる。 */
    long countByUserIdAndStatusAndNotifiedAtIsNull(Long userId, DownloadStatus status);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
            UPDATE DownloadHistory h
            SET h.notifiedAt = :now
            WHERE h.userId = :userId
              AND h.status IN (com.raditomo.history.entity.DownloadStatus.FAILED,
                               com.raditomo.history.entity.DownloadStatus.EXPIRED)
              AND h.notifiedAt IS NULL
            """)
    int markAllErrorsNotified(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("now") OffsetDateTime now);

    Optional<DownloadHistory> findFirstByUserIdAndStationIdAndBroadcastStartAtAndStatus(
            Long userId, String stationId, OffsetDateTime broadcastStartAt, DownloadStatus status);

    /**
     * 同じ放送 (user × station × broadcastStartAt) の SUCCESS 履歴を物理削除する。
     * F2 が新規 SUCCESS を INSERT する直前に呼び、過去の SUCCESS（再 DL 元）と
     * 重複しないようにする。FAILED / EXPIRED は失敗ログとして残すので削除しない。
     */
    @org.springframework.data.jpa.repository.Modifying
    int deleteByUserIdAndStationIdAndBroadcastStartAtAndStatus(
            Long userId, String stationId, OffsetDateTime broadcastStartAt, DownloadStatus status);

    /**
     * 録音ファイルが残っている SUCCESS 履歴を全件返す。
     * 件数集計やグルーピングは呼び出し側（タイトル正規化が必要）で行う。
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT h FROM DownloadHistory h
            WHERE h.userId = :userId
              AND h.status = com.raditomo.history.entity.DownloadStatus.SUCCESS
              AND h.fileDeletedAt IS NULL
            ORDER BY h.broadcastStartAt DESC
            """)
    List<DownloadHistory> findAvailableRecordings(
            @org.springframework.data.repository.query.Param("userId") Long userId);
}
