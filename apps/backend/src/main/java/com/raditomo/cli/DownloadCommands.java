package com.raditomo.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raditomo.batch.entity.BatchType;
import com.raditomo.batch.entity.TriggeredBy;
import com.raditomo.batch.service.F2BatchRunner;
import com.raditomo.batch.service.F4BatchRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.HashMap;
import java.util.Map;

/**
 * 番組DL関連の CLI サブコマンド。
 *
 *   raditomo download                          # F4_F2 連鎖
 *   raditomo download-programs                 # F4 単独
 *   raditomo download-audio --date 20260424    # F2 単独
 *
 * 設計判断: ユーザーが嫌った "F4-F2" のような技術名を避け、download / download-programs /
 * download-audio という機能名を使う。
 */
public class DownloadCommands {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Component
    @Command(name = "download", description = "番組表取得 + タイムフリーダウンロード（F4 → F2 連鎖）")
    @RequiredArgsConstructor
    @Slf4j
    static class DownloadCommand implements Runnable {

        private final F4BatchRunner f4BatchRunner;

        @Option(names = "--date", description = "対象放送日 YYYYMMDD（省略時は全候補）")
        String date;

        @Option(names = "--force", description = "成功済みでも再ダウンロード", defaultValue = "false")
        boolean force;

        @Override
        public void run() {
            String options = buildOptionsJson(date, force);
            log.info("CLI: starting download (F4_F2) options={}", options);
            // CLI モードは Picocli runnable が return すると JVM が即 exit するため、
            // event + @Async で連鎖させると F2 が中断される。同期版を使う。
            var exec = f4BatchRunner.runF4F2Synchronously(TriggeredBy.CLI, null, options);
            System.out.printf("F4_F2 完了: id=%d status=%s summary=%s%n",
                    exec.getId(), exec.getStatus(), exec.getSummary());
        }
    }

    @Component
    @Command(name = "download-programs", description = "番組表のみ取得（F4 単独）")
    @RequiredArgsConstructor
    @Slf4j
    static class DownloadProgramsCommand implements Runnable {

        private final F4BatchRunner f4BatchRunner;

        @Override
        public void run() {
            log.info("CLI: starting download-programs (F4)");
            var exec = f4BatchRunner.runF4(BatchType.F4, TriggeredBy.CLI, null, null);
            System.out.printf("F4 完了: id=%d status=%s summary=%s%n",
                    exec.getId(), exec.getStatus(), exec.getSummary());
        }
    }

    @Component
    @Command(name = "download-audio", description = "DBの番組表をもとに音声をダウンロード（F2 単独）")
    @RequiredArgsConstructor
    @Slf4j
    static class DownloadAudioCommand implements Runnable {

        private final F2BatchRunner f2BatchRunner;

        @Option(names = "--date", description = "対象放送日 YYYYMMDD（省略時は全候補）")
        String date;

        @Option(names = "--force", description = "成功済みでも再ダウンロード", defaultValue = "false")
        boolean force;

        @Override
        public void run() {
            String options = buildOptionsJson(date, force);
            log.info("CLI: starting download-audio (F2) options={}", options);
            var exec = f2BatchRunner.runF2(TriggeredBy.CLI, null, options);
            System.out.printf("F2 完了: id=%d status=%s summary=%s%n",
                    exec.getId(), exec.getStatus(), exec.getSummary());
        }
    }

    private static String buildOptionsJson(String date, boolean force) {
        Map<String, Object> opts = new HashMap<>();
        if (date != null && !date.isBlank()) opts.put("date", date);
        if (force) opts.put("force", true);
        if (opts.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(opts);
        } catch (Exception e) {
            return null;
        }
    }
}
