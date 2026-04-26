package com.raditomo.history.service;

import com.raditomo.common.time.JstTimes;
import com.raditomo.common.util.Sanitizer;
import com.raditomo.history.entity.DownloadHistory;
import com.raditomo.history.entity.DownloadStatus;
import com.raditomo.history.entity.PlaybackPosition;
import com.raditomo.history.repository.DownloadHistoryRepository;
import com.raditomo.history.repository.PlaybackPositionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecordingService {

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(JstTimes.JST);

    private final DownloadHistoryRepository historyRepository;
    private final PlaybackPositionRepository positionRepository;
    private final Clock clock;

    public Optional<DownloadHistory> findOwnedById(Long userId, Long historyId) {
        return historyRepository.findById(historyId).filter(h -> h.getUserId().equals(userId));
    }

    /**
     * Nginx の location ~ ^/hls/([^/]+)/(.+)$ にマッピングした URL を返す。
     * フェーズ7で署名付きパスに置き換える予定。
     */
    public String hlsUrl(DownloadHistory h) {
        String title = Sanitizer.forFileName(h.getProgramTitle());
        String dt = DT_FMT.format(h.getBroadcastStartAt().toInstant());
        return "/hls/" + h.getUserId() + "/" + title + "/" + dt + "/playlist.m3u8";
    }

    public boolean isReDownloadable(DownloadHistory h) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return now.isBefore(JstTimes.expiresAt(h.getBroadcastStartAt()));
    }

    @Transactional
    public boolean deleteRecordingFiles(Long userId, Long historyId) {
        Optional<DownloadHistory> owned = findOwnedById(userId, historyId);
        if (owned.isEmpty()) return false;
        DownloadHistory h = owned.get();
        deletePathIfExists(h.getMp3Path());
        deleteHlsDir(h.getHlsPath());
        h.setMp3Path(null);
        h.setHlsPath(null);
        h.setFileDeletedAt(OffsetDateTime.now(clock));
        historyRepository.save(h);
        return true;
    }

    public PlaybackPosition getOrZero(Long userId, Long historyId) {
        return positionRepository.findById(new PlaybackPosition.Pk(userId, historyId))
                .orElse(PlaybackPosition.builder()
                        .userId(userId).downloadHistoryId(historyId)
                        .positionSeconds(0)
                        .updatedAt(OffsetDateTime.now(clock))
                        .build());
    }

    @Transactional
    public PlaybackPosition savePosition(Long userId, Long historyId, int seconds) {
        PlaybackPosition.Pk pk = new PlaybackPosition.Pk(userId, historyId);
        PlaybackPosition p = positionRepository.findById(pk)
                .orElse(PlaybackPosition.builder()
                        .userId(userId).downloadHistoryId(historyId).build());
        p.setPositionSeconds(seconds);
        p.setUpdatedAt(OffsetDateTime.now(clock));
        return positionRepository.save(p);
    }

    private void deletePathIfExists(String path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(Path.of(path));
        } catch (IOException e) {
            log.warn("Failed to delete file: {} ({})", path, e.toString());
        }
    }

    private void deleteHlsDir(String hlsPlaylistPath) {
        if (hlsPlaylistPath == null) return;
        Path dir = Path.of(hlsPlaylistPath).getParent();
        if (dir == null) return;
        try (var stream = Files.list(dir)) {
            stream.forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
            Files.deleteIfExists(dir);
        } catch (IOException e) {
            log.warn("Failed to delete HLS dir: {} ({})", dir, e.toString());
        }
    }
}
