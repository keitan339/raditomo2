package com.raditomo.radiko.download;

import com.raditomo.radiko.RadikoHttp;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * タイムフリー音声のチャンク並列ダウンロード（2026/01 仕様変更後）。
 *
 * 旧仕様（〜2026/01/26）: radiko.jp/v2/api/ts/playlist.m3u8 → 廃止
 *
 * 新仕様:
 * 1. {@code https://radiko.jp/v3/station/stream/pc_html5/{stationId}.xml} を取得
 *    → {@code <url timefree="1" areafree="0">} の {@code <playlist_create_url>} を抽出
 * 2. プレイリスト URL（例: tf-f-rpaa-radiko.smartstream.ne.jp/tf/playlist.m3u8）に
 *    station_id / start_at / ft / seek / end_at / to / l / lsid / type=c を付けて GET
 *    → マスタープレイリストが返る
 * 3. マスタープレイリストの URL を辿るとセグメント (.aac) リストの medialist が得られる
 * 4. medialist 内の各セグメント URL を並列 DL → 順序通り連結保存
 *
 * 1リクエストあたりの最大長は 300 秒。番組が 300 秒を超える場合は 300 秒単位に分割し、
 * 各チャンク（プレイリスト分割）ごとに上記 1〜4 を実行して連結する。
 *
 * 認証ヘッダ: X-Radiko-AuthToken, X-Radiko-AreaId（auth2 で取得した areaId）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RadikoTimefreeDownloader {

    private static final DateTimeFormatter RADIKO_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(java.time.ZoneId.of("Asia/Tokyo"));
    /** 1 プレイリストリクエストあたりの最大秒数（rec_radiko_ts の仕様に準拠）。 */
    private static final int MAX_CHUNK_SECONDS = 300;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern AREAFREE_TIMEFREE_URL = Pattern.compile(
            "<url\\s+[^>]*areafree=\"0\"[^>]*timefree=\"1\"[^>]*>\\s*<playlist_create_url>\\s*([^<]+?)\\s*</playlist_create_url>"
                    + "|<url\\s+[^>]*timefree=\"1\"[^>]*areafree=\"0\"[^>]*>\\s*<playlist_create_url>\\s*([^<]+?)\\s*</playlist_create_url>");

    private final RadikoProperties props;
    private final RadikoAuthService authService;
    private final HttpClient radikoHttpClient;

    /**
     * 指定範囲のタイムフリー音声を AAC として 1 ファイルに連結ダウンロード。
     */
    public DownloadStats download(String stationId, OffsetDateTime ft, OffsetDateTime to, Path outputAac) {
        RadikoAuthToken auth = authService.getToken();
        String playlistCreateUrl = fetchPlaylistCreateUrl(stationId);

        long totalSeconds = Duration.between(ft, to).toSeconds();
        if (totalSeconds <= 0) {
            throw new RadikoDownloadException("Invalid time range: ft=" + ft + " to=" + to);
        }

        // 300 秒チャンクに分割して順次セグメント URL を集める。
        List<String> allSegmentUrls = new ArrayList<>();
        OffsetDateTime cursor = ft;
        while (cursor.isBefore(to)) {
            OffsetDateTime chunkEnd = cursor.plusSeconds(MAX_CHUNK_SECONDS);
            if (chunkEnd.isAfter(to)) chunkEnd = to;
            int lSec = (int) Duration.between(cursor, chunkEnd).toSeconds();
            String masterUrl = buildPlaylistUrl(playlistCreateUrl, stationId, cursor, chunkEnd, lSec);
            String masterBody = httpGet(masterUrl, auth);
            String mediaUrl = extractMediaPlaylistUrl(masterBody, masterUrl);
            String mediaBody = httpGet(mediaUrl, auth);
            allSegmentUrls.addAll(extractChunkUrls(mediaBody, mediaUrl));
            cursor = chunkEnd;
        }

        if (allSegmentUrls.isEmpty()) {
            throw new RadikoDownloadException("No segments found for stationId=" + stationId);
        }

        try {
            Files.createDirectories(outputAac.getParent());
        } catch (IOException e) {
            throw new RadikoDownloadException("Cannot create output dir: " + outputAac.getParent(), e);
        }

        byte[][] chunks = downloadChunks(allSegmentUrls, auth);
        try (var out = Files.newOutputStream(outputAac)) {
            for (byte[] chunk : chunks) {
                out.write(chunk);
            }
        } catch (IOException e) {
            throw new RadikoDownloadException("Failed to write merged file: " + outputAac, e);
        }
        long totalBytes = 0;
        for (byte[] chunk : chunks) totalBytes += chunk.length;
        log.info("Download completed: stationId={} segments={} bytes={} file={}",
                stationId, chunks.length, totalBytes, outputAac);
        return new DownloadStats(chunks.length, totalBytes);
    }

    /** {@code GET /v3/station/stream/pc_html5/{stationId}.xml} から timefree/areafree=0 の URL を抽出。 */
    String fetchPlaylistCreateUrl(String stationId) {
        String url = props.baseUrl() + "/v3/station/stream/pc_html5/" + stationId + ".xml";
        String body = simpleHttpGet(url);
        Matcher m = AREAFREE_TIMEFREE_URL.matcher(body);
        if (!m.find()) {
            throw new RadikoDownloadException("Failed to find timefree playlist URL for " + stationId);
        }
        // 2 つの代替パターンのうち、マッチしたグループを使う。
        return m.group(1) != null ? m.group(1).trim() : m.group(2).trim();
    }

    String buildPlaylistUrl(String playlistCreateUrl, String stationId,
                            OffsetDateTime ft, OffsetDateTime to, int lSec) {
        String ftStr = RADIKO_FORMAT.format(ft);
        String toStr = RADIKO_FORMAT.format(to);
        String lsid = HexFormat.of().formatHex(randomBytes(16));
        return playlistCreateUrl
                + "?station_id=" + stationId
                + "&start_at=" + ftStr
                + "&ft=" + ftStr
                + "&seek=" + ftStr
                + "&end_at=" + toStr
                + "&to=" + toStr
                + "&l=" + lSec
                + "&lsid=" + lsid
                + "&type=c";
    }

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
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

    private byte[][] downloadChunks(List<String> urls, RadikoAuthToken auth) {
        int concurrency = Math.max(1, props.downloadConcurrency());
        Semaphore semaphore = new Semaphore(concurrency);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<byte[]>> futures = new ArrayList<>(urls.size());
            for (String url : urls) {
                futures.add(pool.submit(() -> {
                    semaphore.acquire();
                    try {
                        return downloadChunkWithRetry(url, auth);
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

    private byte[] downloadChunkWithRetry(String url, RadikoAuthToken auth) {
        int maxAttempts = Math.max(1, props.chunkRetry());
        IOException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return downloadChunkOnce(url, auth);
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

    private byte[] downloadChunkOnce(String url, RadikoAuthToken auth) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("X-Radiko-AuthToken", auth.authToken())
                .header("X-Radiko-AreaId", auth.areaId())
                .timeout(Duration.ofSeconds(props.httpReadTimeoutSeconds()))
                .build();
        HttpResponse<byte[]> resp = radikoHttpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + resp.statusCode() + " for " + url);
        }
        return resp.body();
    }

    /** 認証ヘッダ付き GET（プレイリストや medialist 取得用）。 */
    private String httpGet(String url, RadikoAuthToken auth) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("X-Radiko-AuthToken", auth.authToken())
                .header("X-Radiko-AreaId", auth.areaId())
                .timeout(Duration.ofSeconds(props.httpReadTimeoutSeconds()))
                .build();
        try {
            // バイト受信 + RadikoHttp.decodeBody で gzip を解凍する
            HttpResponse<byte[]> resp = radikoHttpClient.send(
                    req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                throw new RadikoDownloadException("HTTP " + resp.statusCode() + " for " + url);
            }
            return RadikoHttp.decodeBody(resp);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RadikoDownloadException("Failed: " + url, e);
        }
    }

    /** 認証ヘッダ不要の GET（station stream xml 取得用）。 */
    private String simpleHttpGet(String url) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(props.httpReadTimeoutSeconds()))
                .build();
        try {
            // バイト受信 + RadikoHttp.decodeBody で gzip を解凍する
            HttpResponse<byte[]> resp = radikoHttpClient.send(
                    req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                throw new RadikoDownloadException("HTTP " + resp.statusCode() + " for " + url);
            }
            return RadikoHttp.decodeBody(resp);
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
