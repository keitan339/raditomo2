package com.raditomo.batch.service;

import com.raditomo.common.time.JstTimes;
import com.raditomo.common.util.Sanitizer;
import com.raditomo.history.entity.DownloadHistory;
import com.raditomo.history.entity.DownloadStatus;
import com.raditomo.history.repository.DownloadHistoryRepository;
import com.raditomo.history.service.RecordingTitleGrouper;
import com.raditomo.radiko.download.RadikoTimefreeDownloader;
import com.raditomo.radiko.encode.HlsConverter;
import com.raditomo.radiko.encode.Mp3Encoder;
import com.raditomo.registration.entity.DownloadRegistration;
import com.raditomo.registration.entity.RegistrationStatus;
import com.raditomo.registration.entity.RegistrationType;
import com.raditomo.registration.repository.DownloadRegistrationRepository;
import com.raditomo.station.entity.Station;
import com.raditomo.station.repository.StationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 1番組ずつ直列でダウンロードする F2 本体。
 *
 * 各候補の処理:
 * 1. 期限切れチェック（broadcastStartAt の 8 日後 5:00 JST 以降なら EXPIRED）
 * 2. 期限内なら最大 3 回までリトライ:
 *    a. AAC を一時ファイルにダウンロード
 *    b. MP3 へエンコード（ID3 タグ付与）
 *    c. HLS へ変換
 * 3. 成否を download_histories に記録、ONCE は status を COMPLETED/EXPIRED に更新
 *
 * リトライ間で部分ファイル（AAC / MP3 / HLS）を必ず削除してから再実行する。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimefreeDownloadService {

    private static final int MAX_ATTEMPTS = 3;
    private static final DateTimeFormatter PATH_DATETIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(JstTimes.JST);

    private final RadikoTimefreeDownloader downloader;
    private final Mp3Encoder mp3Encoder;
    private final HlsConverter hlsConverter;
    private final DownloadHistoryRepository historyRepository;
    private final DownloadRegistrationRepository registrationRepository;
    private final StationRepository stationRepository;
    private final RecordingTitleGrouper titleGrouper;
    private final Clock clock;

    @Value("${raditomo.recordings-base-path}")
    private String recordingsBasePath;

    /**
     * 与えられた候補リストを順に処理する。例外は飲み込み、結果は履歴に記録される。
     * 戻り値は成功・失敗・期限切れ件数のサマリ。
     */
    public DownloadSummary processAll(List<DownloadCandidate> candidates) {
        int success = 0, failed = 0, expired = 0;
        for (DownloadCandidate c : candidates) {
            try {
                Result r = processOne(c);
                switch (r) {
                    case SUCCESS -> success++;
                    case FAILED -> failed++;
                    case EXPIRED -> expired++;
                }
            } catch (RuntimeException e) {
                log.error("Unexpected error processing candidate stationId={} startAt={}",
                        c.stationId(), c.broadcastStartAt(), e);
                failed++;
            }
        }
        return new DownloadSummary(success, failed, expired);
    }

    @Transactional
    Result processOne(DownloadCandidate c) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime expiresAt = JstTimes.expiresAt(c.broadcastStartAt());
        if (!now.isBefore(expiresAt)) {
            recordExpired(c);
            updateRegistrationIfOnce(c, RegistrationStatus.EXPIRED);
            return Result.EXPIRED;
        }

        Path mp3Path = mp3Path(c);
        Path hlsDir = hlsDir(c);
        Path aacTemp = mp3Path.resolveSibling(mp3Path.getFileName() + ".tmp.aac");

        Throwable lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                cleanupPartial(aacTemp, mp3Path, hlsDir);
                Files.createDirectories(mp3Path.getParent());

                downloader.download(c.stationId(), c.broadcastStartAt(), c.broadcastEndAt(), aacTemp);

                String stationName = stationRepository.findById(c.stationId())
                        .map(Station::getName).orElse(c.stationId());
                long durationMillis = Duration.between(c.broadcastStartAt(), c.broadcastEndAt()).toMillis();
                String titleWithDateTime = c.title() + "_" + PATH_DATETIME.format(c.broadcastStartAt().toInstant());
                Mp3Encoder.Mp3Metadata meta = new Mp3Encoder.Mp3Metadata(
                        titleWithDateTime,
                        c.performers(),
                        stationName,
                        c.broadcastStartAt().atZoneSameInstant(JstTimes.JST).toLocalDate(),
                        durationMillis);
                mp3Encoder.encode(aacTemp, mp3Path, meta);
                hlsConverter.convert(mp3Path, hlsDir);

                long size = Files.size(mp3Path);
                Files.deleteIfExists(aacTemp);
                recordSuccess(c, mp3Path, hlsDir, durationMillis, size);
                updateRegistrationIfOnce(c, RegistrationStatus.COMPLETED);
                return Result.SUCCESS;
            } catch (RuntimeException | IOException e) {
                lastError = e;
                log.warn("Download attempt {}/{} failed: stationId={} startAt={} cause={}",
                        attempt, MAX_ATTEMPTS, c.stationId(), c.broadcastStartAt(), e.toString());
            }
        }
        cleanupPartial(aacTemp, mp3Path, hlsDir);
        recordFailure(c, lastError);
        return Result.FAILED;
    }

    private void cleanupPartial(Path aac, Path mp3, Path hlsDir) {
        try { Files.deleteIfExists(aac); } catch (IOException ignored) {}
        try { Files.deleteIfExists(mp3); } catch (IOException ignored) {}
        if (Files.isDirectory(hlsDir)) {
            try (var s = Files.list(hlsDir)) {
                s.forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            } catch (IOException ignored) {}
        }
    }

    Path mp3Path(DownloadCandidate c) {
        // フォルダ名はグループキー（「○時台」「(N)」等を取り除いた共通名）。
        // ファイル名は実タイトルを残し、同じグループ内でも放送回ごとに区別できるようにする。
        String folder = Sanitizer.forFileName(titleGrouper.groupKey(c.title()));
        String filename = Sanitizer.forFileName(c.title());
        String dt = PATH_DATETIME.format(c.broadcastStartAt().toInstant());
        return Path.of(recordingsBasePath, String.valueOf(c.userId()),
                "mp3", folder, filename + "_" + dt + ".mp3");
    }

    Path hlsDir(DownloadCandidate c) {
        String folder = Sanitizer.forFileName(titleGrouper.groupKey(c.title()));
        String filename = Sanitizer.forFileName(c.title());
        String dt = PATH_DATETIME.format(c.broadcastStartAt().toInstant());
        // グループフォルダの中で「番組タイトル_放送日時」のサブフォルダを作る。
        return Path.of(recordingsBasePath, String.valueOf(c.userId()),
                "hls", folder, filename + "_" + dt);
    }

    private void recordSuccess(DownloadCandidate c, Path mp3, Path hlsDir, long durationMillis, long fileSize) {
        // 同じ放送の既存 SUCCESS 履歴は重複防止のため物理削除する
        // （force=true 再実行や、過去にライブラリ削除された履歴 SUCCESS の置き換え）。
        // FAILED / EXPIRED は失敗ログとして保持。
        int removed = historyRepository.deleteByUserIdAndStationIdAndBroadcastStartAtAndStatus(
                c.userId(), c.stationId(), c.broadcastStartAt(), DownloadStatus.SUCCESS);
        if (removed > 0) {
            log.info("Replaced {} existing SUCCESS history for stationId={} startAt={}",
                    removed, c.stationId(), c.broadcastStartAt());
        }
        DownloadHistory hist = DownloadHistory.builder()
                .userId(c.userId())
                .registrationId(c.registrationId())
                .stationId(c.stationId())
                .programTitle(c.title())
                .performers(c.performers())
                .broadcastStartAt(c.broadcastStartAt())
                .broadcastEndAt(c.broadcastEndAt())
                .status(DownloadStatus.SUCCESS)
                .mp3Path(mp3.toString())
                .hlsPath(hlsDir.resolve("playlist.m3u8").toString())
                .durationSeconds((int) (durationMillis / 1000))
                .fileSizeBytes(fileSize)
                .build();
        historyRepository.save(hist);
    }

    private void recordFailure(DownloadCandidate c, Throwable cause) {
        DownloadHistory hist = DownloadHistory.builder()
                .userId(c.userId())
                .registrationId(c.registrationId())
                .stationId(c.stationId())
                .programTitle(c.title())
                .performers(c.performers())
                .broadcastStartAt(c.broadcastStartAt())
                .broadcastEndAt(c.broadcastEndAt())
                .status(DownloadStatus.FAILED)
                .errorMessage(cause == null ? "unknown" : cause.toString())
                .retryCount((short) MAX_ATTEMPTS)
                .build();
        historyRepository.save(hist);
    }

    private void recordExpired(DownloadCandidate c) {
        DownloadHistory hist = DownloadHistory.builder()
                .userId(c.userId())
                .registrationId(c.registrationId())
                .stationId(c.stationId())
                .programTitle(c.title())
                .performers(c.performers())
                .broadcastStartAt(c.broadcastStartAt())
                .broadcastEndAt(c.broadcastEndAt())
                .status(DownloadStatus.EXPIRED)
                .build();
        historyRepository.save(hist);
    }

    private void updateRegistrationIfOnce(DownloadCandidate c, RegistrationStatus status) {
        if (c.registrationType() != RegistrationType.ONCE || c.registrationId() == null) return;
        Optional<DownloadRegistration> r = registrationRepository.findById(c.registrationId());
        r.ifPresent(reg -> {
            reg.setStatus(status);
            registrationRepository.save(reg);
        });
    }

    public enum Result { SUCCESS, FAILED, EXPIRED }

    public record DownloadSummary(int success, int failed, int expired) {
        public int total() { return success + failed + expired; }
    }
}
