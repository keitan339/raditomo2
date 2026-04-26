package com.raditomo.radiko.encode;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * ffmpeg コマンドの起動ラッパー。テストでモック差し替えしやすいよう interface 化している。
 */
public interface FfmpegRunner {

    /**
     * ffmpeg を起動し、終了コードを返す。標準エラー出力はログに記録する。
     *
     * @param command ffmpeg を含む完全なコマンド配列
     * @return 終了コード
     */
    int run(List<String> command) throws IOException, InterruptedException;

    @Component
    @Slf4j
    class Default implements FfmpegRunner {
        @Override
        public int run(List<String> command) throws IOException, InterruptedException {
            log.info("Executing: {}", String.join(" ", command));
            ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("ffmpeg: {}", line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("ffmpeg exited with code {}", exitCode);
            }
            return exitCode;
        }
    }
}
