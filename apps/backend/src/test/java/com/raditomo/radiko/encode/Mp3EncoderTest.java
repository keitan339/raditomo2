package com.raditomo.radiko.encode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class Mp3EncoderTest {

    private FfmpegRunner ffmpeg;
    private Mp3Encoder encoder;

    @BeforeEach
    void setup() {
        ffmpeg = mock(FfmpegRunner.class);
        encoder = new Mp3Encoder(ffmpeg);
    }

    @Test
    void buildFfmpegCommand_includesBitrateAndMetadata() {
        Mp3Encoder.Mp3Metadata meta = new Mp3Encoder.Mp3Metadata(
                "朝の番組_20260424-0500", "朝の番組", "出演者A", "TBSラジオ",
                LocalDate.of(2026, 4, 24), 3_600_000);
        List<String> cmd = encoder.buildFfmpegCommand(
                Path.of("/tmp/in.aac"), Path.of("/tmp/out.mp3"), meta);

        assertThat(cmd).startsWith("ffmpeg", "-y", "-i", "/tmp/in.aac");
        assertThat(cmd).containsSequence("-c:a", "libmp3lame");
        assertThat(cmd).containsSequence("-b:a", "128k");
        assertThat(cmd).containsSequence("-id3v2_version", "3");
        assertThat(cmd).containsSequence("-metadata", "title=朝の番組_20260424-0500");
        assertThat(cmd).containsSequence("-metadata", "artist=出演者A");
        // Album は素の番組名（_YYYYMMDD-HHMM を含まない）
        assertThat(cmd).containsSequence("-metadata", "album=朝の番組");
        // Album Artist は放送局の和名
        assertThat(cmd).containsSequence("-metadata", "album_artist=TBSラジオ");
        assertThat(cmd).containsSequence("-metadata", "date=20260424");
        assertThat(cmd).containsSequence("-metadata", "year=2026");
        assertThat(cmd).endsWith("/tmp/out.mp3");
    }

    @Test
    void buildFfmpegCommand_skipsEmptyMetadata() {
        Mp3Encoder.Mp3Metadata meta = new Mp3Encoder.Mp3Metadata(
                "T", null, null, "", null, 0);
        List<String> cmd = encoder.buildFfmpegCommand(
                Path.of("in.aac"), Path.of("out.mp3"), meta);

        assertThat(cmd).contains("-metadata", "title=T");
        assertThat(cmd).noneMatch(s -> s.startsWith("artist="));
        assertThat(cmd).noneMatch(s -> s.startsWith("album="));
        assertThat(cmd).noneMatch(s -> s.startsWith("album_artist="));
        assertThat(cmd).noneMatch(s -> s.startsWith("date="));
        assertThat(cmd).noneMatch(s -> s.startsWith("year="));
    }

    @Test
    void encode_throwsWhenInputMissing(@TempDir Path tmp) {
        Mp3Encoder.Mp3Metadata meta = new Mp3Encoder.Mp3Metadata(
                "t", null, null, null, null, 0);
        assertThatThrownBy(() -> encoder.encode(tmp.resolve("missing.aac"), tmp.resolve("out.mp3"), meta))
                .isInstanceOf(Mp3Encoder.Mp3EncodeException.class)
                .hasMessageContaining("AAC input not found");
    }

    @Test
    void encode_invokesFfmpegAndCleansUpOnNonZeroExit(@TempDir Path tmp) throws Exception {
        Path aac = tmp.resolve("in.aac");
        Files.writeString(aac, "fake");
        Path mp3 = tmp.resolve("out.mp3");
        Files.writeString(mp3, "stale"); // ffmpeg がここに上書きする想定。失敗時に削除されることを検証
        when(ffmpeg.run(any())).thenReturn(1);

        Mp3Encoder.Mp3Metadata meta = new Mp3Encoder.Mp3Metadata("t", null, null, null, null, 0);
        assertThatThrownBy(() -> encoder.encode(aac, mp3, meta))
                .isInstanceOf(Mp3Encoder.Mp3EncodeException.class);
        assertThat(Files.exists(mp3)).isFalse();
    }

    @Test
    void encode_succeedsAndCallsFfmpegOnce(@TempDir Path tmp) throws Exception {
        Path aac = tmp.resolve("in.aac");
        Files.writeString(aac, "fake");
        Path mp3 = tmp.resolve("out.mp3");
        when(ffmpeg.run(any())).thenAnswer(inv -> {
            // ffmpeg が成功する想定で空ファイルを置く
            Files.writeString(mp3, "");
            return 0;
        });

        Mp3Encoder.Mp3Metadata meta = new Mp3Encoder.Mp3Metadata("t", null, null, null, null, 0);
        encoder.encode(aac, mp3, meta);

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.captor();
        verify(ffmpeg).run(captor.capture());
        assertThat(captor.getValue()).startsWith("ffmpeg");
    }
}
