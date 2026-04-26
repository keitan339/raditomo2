package com.raditomo.radiko.encode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * MP3 → HLS 変換（事前変換、再生時のレスポンス向上のため）。
 *
 * 出力構成:
 *   {hlsDir}/playlist.m3u8
 *   {hlsDir}/segment_000.ts
 *   {hlsDir}/segment_001.ts
 *   ...
 *
 * 失敗時は出力ディレクトリ配下を全削除。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HlsConverter {

    private final FfmpegRunner ffmpeg;

    public void convert(Path mp3Input, Path hlsDir) {
        if (!Files.exists(mp3Input)) {
            throw new HlsConversionException("MP3 input not found: " + mp3Input);
        }
        try {
            Files.createDirectories(hlsDir);
        } catch (IOException e) {
            throw new HlsConversionException("Cannot create HLS dir: " + hlsDir, e);
        }

        List<String> cmd = buildFfmpegCommand(mp3Input, hlsDir);
        try {
            int exit = ffmpeg.run(cmd);
            if (exit != 0) {
                cleanup(hlsDir);
                throw new HlsConversionException("ffmpeg exited with code " + exit);
            }
        } catch (IOException e) {
            cleanup(hlsDir);
            throw new HlsConversionException("HLS conversion failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cleanup(hlsDir);
            throw new HlsConversionException("Interrupted during HLS conversion", e);
        }
    }

    /** package-private for verification in tests. */
    List<String> buildFfmpegCommand(Path mp3, Path hlsDir) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(mp3.toString());
        cmd.add("-c:a");
        cmd.add("aac");
        cmd.add("-b:a");
        cmd.add("128k");
        cmd.add("-f");
        cmd.add("hls");
        cmd.add("-hls_time");
        cmd.add("10");
        cmd.add("-hls_list_size");
        cmd.add("0");
        cmd.add("-hls_segment_filename");
        cmd.add(hlsDir.resolve("segment_%03d.ts").toString());
        cmd.add(hlsDir.resolve("playlist.m3u8").toString());
        return cmd;
    }

    private void cleanup(Path hlsDir) {
        try (Stream<Path> stream = Files.list(hlsDir)) {
            stream.forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    public static class HlsConversionException extends RuntimeException {
        public HlsConversionException(String msg) { super(msg); }
        public HlsConversionException(String msg, Throwable cause) { super(msg, cause); }
    }
}
