package com.raditomo.radiko.encode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HlsConverterTest {

    private FfmpegRunner ffmpeg;
    private HlsConverter converter;

    @BeforeEach
    void setup() {
        ffmpeg = mock(FfmpegRunner.class);
        converter = new HlsConverter(ffmpeg);
    }

    @Test
    void buildFfmpegCommand_includesHlsOptions(@TempDir Path tmp) {
        List<String> cmd = converter.buildFfmpegCommand(
                Path.of("/in.mp3"), tmp);

        assertThat(cmd).containsSequence("-c:a", "aac");
        assertThat(cmd).containsSequence("-b:a", "128k");
        assertThat(cmd).containsSequence("-f", "hls");
        assertThat(cmd).containsSequence("-hls_time", "10");
        assertThat(cmd).containsSequence("-hls_list_size", "0");
        assertThat(cmd).contains(tmp.resolve("segment_%03d.ts").toString());
        assertThat(cmd).endsWith(tmp.resolve("playlist.m3u8").toString());
    }

    @Test
    void convert_throwsWhenInputMissing(@TempDir Path tmp) {
        assertThatThrownBy(() -> converter.convert(tmp.resolve("missing.mp3"), tmp.resolve("hls")))
                .isInstanceOf(HlsConverter.HlsConversionException.class);
    }

    @Test
    void convert_cleansUpOnFailure(@TempDir Path tmp) throws Exception {
        Path mp3 = tmp.resolve("in.mp3");
        Files.writeString(mp3, "fake");
        Path hlsDir = tmp.resolve("hls");
        Files.createDirectories(hlsDir);
        Files.writeString(hlsDir.resolve("stale.ts"), "");

        when(ffmpeg.run(any())).thenReturn(1);
        assertThatThrownBy(() -> converter.convert(mp3, hlsDir))
                .isInstanceOf(HlsConverter.HlsConversionException.class);
        // クリーンアップでディレクトリ配下が消えていることを確認
        try (var s = Files.list(hlsDir)) {
            assertThat(s.count()).isZero();
        }
    }

    @Test
    void convert_succeedsAndInvokesFfmpegOnce(@TempDir Path tmp) throws Exception {
        Path mp3 = tmp.resolve("in.mp3");
        Files.writeString(mp3, "fake");
        Path hlsDir = tmp.resolve("hls");
        when(ffmpeg.run(any())).thenAnswer(inv -> {
            // ffmpeg が成功する想定でファイルを作る
            Files.createDirectories(hlsDir);
            Files.writeString(hlsDir.resolve("playlist.m3u8"), "#EXTM3U\n");
            return 0;
        });
        converter.convert(mp3, hlsDir);
        verify(ffmpeg, times(1)).run(any());
        assertThat(Files.exists(hlsDir.resolve("playlist.m3u8"))).isTrue();
    }
}
