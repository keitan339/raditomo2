package com.raditomo.radiko.encode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * AAC（チャンク連結済み）→ MP3 変換 + ID3v2.3 タグ付与。
 *
 * 設計判断:
 * - 128 kbps CBR 固定（要件 F2）
 * - ID3 は ffmpeg の -metadata で付与（title/artist/album/album_artist/date/year）
 *   - mp3agic 0.9.1 が TLEN を公開していないため、duration は MP3 フレームヘッダに任せる
 *     （多くのプレイヤーはフレームヘッダから duration を算出する）
 *
 * 失敗時は出力 MP3 を削除する。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Mp3Encoder {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final FfmpegRunner ffmpeg;

    /**
     * 連結済み単一 AAC を MP3 へ変換し ID3 タグ付与。
     */
    public void encode(Path aacInput, Path mp3Output, Mp3Metadata metadata) {
        if (!Files.exists(aacInput)) {
            throw new Mp3EncodeException("AAC input not found: " + aacInput);
        }
        try {
            Files.createDirectories(mp3Output.getParent());
        } catch (IOException e) {
            throw new Mp3EncodeException("Cannot create output dir", e);
        }

        List<String> cmd = buildFfmpegCommand(aacInput, mp3Output, metadata);
        try {
            int exit = ffmpeg.run(cmd);
            if (exit != 0) {
                Files.deleteIfExists(mp3Output);
                throw new Mp3EncodeException("ffmpeg exited with code " + exit);
            }
        } catch (IOException e) {
            cleanup(mp3Output);
            throw new Mp3EncodeException("MP3 encode failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cleanup(mp3Output);
            throw new Mp3EncodeException("Interrupted during MP3 encode", e);
        }
    }

    /** package-private for test verification. */
    List<String> buildFfmpegCommand(Path aac, Path mp3, Mp3Metadata m) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(aac.toString());
        cmd.add("-c:a");
        cmd.add("libmp3lame");
        cmd.add("-b:a");
        cmd.add("128k");
        cmd.add("-id3v2_version");
        cmd.add("3");
        addMeta(cmd, "title", m.title());
        addMeta(cmd, "artist", m.performers());
        addMeta(cmd, "album", m.albumTitle());
        addMeta(cmd, "album_artist", m.stationName());
        if (m.broadcastDate() != null) {
            addMeta(cmd, "date", DATE_FMT.format(m.broadcastDate()));
            addMeta(cmd, "year", String.valueOf(m.broadcastDate().getYear()));
        }
        cmd.add(mp3.toString());
        return cmd;
    }

    private static void addMeta(List<String> cmd, String key, String value) {
        if (value == null || value.isEmpty()) return;
        cmd.add("-metadata");
        cmd.add(key + "=" + value);
    }

    private void cleanup(Path mp3) {
        try { Files.deleteIfExists(mp3); } catch (IOException ignore) {}
    }

    /**
     * @param title        ID3 Title。要件 F2 通り「番組名_YYYYMMDD-HHMM」を渡す
     * @param albumTitle   ID3 Album。素の番組名（_YYYYMMDD-HHMM を含まない）
     * @param performers   ID3 Artist
     * @param stationName  ID3 Album Artist（放送局の和名）
     */
    public record Mp3Metadata(
            String title,
            String albumTitle,
            String performers,
            String stationName,
            LocalDate broadcastDate,
            long durationMillis
    ) {
    }

    public static class Mp3EncodeException extends RuntimeException {
        public Mp3EncodeException(String msg) { super(msg); }
        public Mp3EncodeException(String msg, Throwable cause) { super(msg, cause); }
    }
}
