package com.raditomo.radiko.download;

import com.raditomo.radiko.RadikoProperties;
import com.raditomo.radiko.auth.RadikoAuthService;
import com.raditomo.radiko.auth.RadikoAuthToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * タイムフリー音声のチャンク並列ダウンロード（2026/01 仕様変更後）。
 *
 * フロー:
 * 1. /v2/api/ts/playlist.m3u8 でマスタープレイリスト取得（認証ヘッダー付き）
 * 2. マスタープレイリストから smartstream.ne.jp 配信のメディアプレイリストURLを抽出
 * 3. メディアプレイリストから .aac セグメントURLを順序通り抽出
 * 4. 並列度 N（デフォルト8）の Virtual Thread で並列DL → 順序通り連結保存
 *
 * 設計判断: 番組単位は直列、チャンクのみ並列（レート制限・エラー処理の単純化のため）。
 *
 * 部分失敗時は最大 chunkRetry 回まで個別チャンクをリトライ。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RadikoTimefreeDownloader {

    private static final DateTimeFormatter RADIKO_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(java.time.ZoneId.of("Asia/Tokyo"));

    private final RadikoProperties props;
    private final RadikoAuthService authService;
    private final HttpClient radikoHttpClient;

    /**
     * 指定範囲のタイムフリー音声を AAC として 1 ファイルに連結ダウンロード。
     *
     * @param stationId 放送局ID
     * @param ft        放送開始（JST）
     * @param to        放送終了（JST）
     * @param outputAac 出力先（既存ファイルは上書き）
     * @return チャンク統計
     */
    public DownloadStats download(String stationId, OffsetDateTime ft, OffsetDateTime to, Path outputAac) {
        RadikoAuthToken auth = authService.getToken();
        String masterUrl = buildPlaylistUrl(stationId, ft, to);
        String masterBody = httpGet(masterUrl, auth.authToken());
        String mediaUrl = extractMediaPlaylistUrl(masterBody, masterUrl);
        String mediaBody = httpGet(mediaUrl, auth.authToken());
        List<String> chunkUrls = extractChunkUrls(mediaBody, mediaUrl);

        if (chunkUrls.isEmpty()) {
            throw new RadikoDownloadException("No chunks found for stationId=" + stationId);
        }

        try {
            Files.createDirectories(outputAac.getParent());
        } catch (IOException e) {
            throw new RadikoDownloadException("Cannot create output dir: " + outputAac.getParent(), e);
        }

        byte[][] chunks = downloadChunks(chunkUrls, auth.authToken());
        try (var out = Files.newOutputStream(outputAac)) {
            for (byte[] chunk : chunks) {
                out.write(chunk);
            }
        } catch (IOException e) {
            throw new RadikoDownloadException("Failed to write merged file: " + outputAac, e);
        }
        long totalBytes = 0;
        for (byte[] chunk : chunks) totalBytes += chunk.length;
        log.info("Download completed: stationId={} chunks={} bytes={} file={}",
                stationId, chunks.length, totalBytes, outputAac);
        return new DownloadStats(chunks.length, totalBytes);
    }

    String buildPlaylistUrl(String stationId, OffsetDateTime ft, OffsetDateTime to) {
        return props.baseUrl() + "/v2/api/ts/playlist.m3u8"
                + "?station_id=" + stationId
                + "&l=15"
                + "&ft=" + RADIKO_FORMAT.format(ft)
                + "&to=" + RADIKO_FORMAT.format(to);
    }

    /** マスタープレイリストから最初のメディアプレイリスト URL を取り出す。 */
    static String extractMediaPlaylistUrl(String masterPlaylist, String baseUrl) {
        String[] lines = masterPlaylist.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            return resolveUrl(baseUrl, trimmed);
        }
        throw new RadikoDownloadException("Master playlist contains no media URL");
    }

    /** メディアプレイリストから順序通りに .aac セグメントURLを抽出。 */
    static List<String> extractChunkUrls(String mediaPlaylist, String baseUrl) {
        List<String> urls = new ArrayList<>();
        String[] lines = mediaPlaylist.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            urls.add(resolveUrl(baseUrl, trimmed));
        }
        return urls;
    }

    private static String resolveUrl(String base, String maybeRelative) {
        if (maybeRelative.startsWith("http://") || maybeRelative.startsWith("https://")) {
            return maybeRelative;
        }
        return URI.create(base).resolve(maybeRelative).toString();
    }

    private byte[][] downloadChunks(List<String> urls, String authToken) {
        int concurrency = Math.max(1, props.downloadConcurrency());
        Semaphore semaphore = new Semaphore(concurrency);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<byte[]>> futures = new ArrayList<>(urls.size());
            for (String url : urls) {
                futures.add(pool.submit(() -> {
                    semaphore.acquire();
                    try {
                        return downloadChunkWithRetry(url, authToken);
                    } finally {
                        semaphore.release();
                    }
                }));
            }
            byte[][] result = new byte[urls.size()][];
            for (int i = 0; i < futures.size(); i++) {
                try {
                    result[i] = futures.get(i).get();
                } catch (ExecutionException e) {
                    throw new RadikoDownloadException("Chunk download failed: " + urls.get(i), e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RadikoDownloadException("Interrupted while downloading chunks", e);
                }
            }
            return result;
        }
    }

    private byte[] downloadChunkWithRetry(String url, String authToken) {
        int maxAttempts = Math.max(1, props.chunkRetry());
        IOException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return downloadChunkOnce(url, authToken);
            } catch (IOException e) {
                last = e;
                log.warn("Chunk download attempt {}/{} failed: {} ({})",
                        attempt, maxAttempts, url, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RadikoDownloadException("Interrupted: " + url, e);
            }
        }
        throw new RadikoDownloadException("All retries exhausted for: " + url, last);
    }

    private byte[] downloadChunkOnce(String url, String authToken) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("X-Radiko-AuthToken", authToken)
                .timeout(Duration.ofSeconds(props.httpReadTimeoutSeconds()))
                .build();
        HttpResponse<byte[]> resp = radikoHttpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + resp.statusCode() + " for " + url);
        }
        return resp.body();
    }

    private String httpGet(String url, String authToken) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("X-Radiko-AuthToken", authToken)
                .timeout(Duration.ofSeconds(props.httpReadTimeoutSeconds()))
                .build();
        try {
            HttpResponse<String> resp = radikoHttpClient.send(
                    req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                throw new RadikoDownloadException("HTTP " + resp.statusCode() + " for " + url);
            }
            return resp.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RadikoDownloadException("Failed: " + url, e);
        }
    }

    public record DownloadStats(int chunkCount, long totalBytes) {}

    public static class RadikoDownloadException extends RuntimeException {
        public RadikoDownloadException(String msg) { super(msg); }
        public RadikoDownloadException(String msg, Throwable cause) { super(msg, cause); }
    }
}
