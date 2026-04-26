package com.raditomo.radiko.auth;

import com.raditomo.radiko.RadikoProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ラジコのデバイスレベル認証（auth1 → 共通鍵スライス → auth2）。
 *
 * 設計ノート:
 * - アカウント不要のフリー会員相当。auth2 が返す area_id がサーバー設置場所のエリア。
 * - トークンは {@code raditomo.radiko.auth-cache-hours}（デフォルト 3h）でキャッシュ。
 * - 共通鍵は {@code raditomo.radiko.auth-key}（rec_radiko_ts 互換）。失効時は要更新。
 *
 * 参考: <a href="https://github.com/uru2/rec_radiko_ts">rec_radiko_ts</a>
 */
@Service
@Slf4j
public class RadikoAuthService {

    private static final String APP = "pc_html5";
    private static final String APP_VERSION = "0.0.1";
    private static final String USER = "dummy_user";
    private static final String DEVICE = "pc";

    private final RadikoProperties props;
    private final HttpClient httpClient;
    private final Clock clock;

    private final ReentrantLock lock = new ReentrantLock();
    private RadikoAuthToken cached;

    public RadikoAuthService(RadikoProperties props, HttpClient radikoHttpClient, Clock clock) {
        this.props = props;
        this.httpClient = radikoHttpClient;
        this.clock = clock;
    }

    /**
     * 有効なトークンを返す。キャッシュがあれば再利用、なければ auth1/auth2 を実行。
     */
    public RadikoAuthToken getToken() {
        Optional<RadikoAuthToken> fresh = currentIfFresh();
        if (fresh.isPresent()) return fresh.get();

        lock.lock();
        try {
            Optional<RadikoAuthToken> raceCheck = currentIfFresh();
            if (raceCheck.isPresent()) return raceCheck.get();
            RadikoAuthToken token = doAuthenticate();
            cached = token;
            return token;
        } finally {
            lock.unlock();
        }
    }

    /** 強制再認証（テスト用 or トークン失効時）。 */
    public RadikoAuthToken refresh() {
        lock.lock();
        try {
            cached = doAuthenticate();
            return cached;
        } finally {
            lock.unlock();
        }
    }

    private Optional<RadikoAuthToken> currentIfFresh() {
        RadikoAuthToken c = cached;
        if (c == null) return Optional.empty();
        Duration ttl = Duration.ofHours(props.authCacheHours());
        if (c.issuedAt().plus(ttl).isAfter(clock.instant())) {
            return Optional.of(c);
        }
        return Optional.empty();
    }

    private RadikoAuthToken doAuthenticate() {
        Auth1Result a1 = auth1();
        String partialKey = derivePartialKey(props.authKey(), a1.keyOffset(), a1.keyLength());
        Auth2Result a2 = auth2(a1.authToken(), partialKey);
        log.info("Radiko auth success: area={} ({})", a2.areaId(), a2.areaName());
        return new RadikoAuthToken(a1.authToken(), a2.areaId(), a2.areaName(), clock.instant());
    }

    private Auth1Result auth1() {
        HttpRequest req = HttpRequest.newBuilder(URI.create(props.baseUrl() + "/v2/api/auth1"))
                .GET()
                .header("X-Radiko-App", APP)
                .header("X-Radiko-App-Version", APP_VERSION)
                .header("X-Radiko-User", USER)
                .header("X-Radiko-Device", DEVICE)
                .timeout(Duration.ofSeconds(props.httpReadTimeoutSeconds()))
                .build();
        HttpResponse<String> resp = send(req, "auth1");
        if (resp.statusCode() / 100 != 2) {
            throw new RadikoAuthException("auth1 failed: status=" + resp.statusCode());
        }
        String token = header(resp, "X-Radiko-AuthToken");
        int offset = Integer.parseInt(header(resp, "X-Radiko-KeyOffset"));
        int length = Integer.parseInt(header(resp, "X-Radiko-KeyLength"));
        return new Auth1Result(token, offset, length);
    }

    private Auth2Result auth2(String token, String partialKey) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(props.baseUrl() + "/v2/api/auth2"))
                .GET()
                .header("X-Radiko-AuthToken", token)
                .header("X-Radiko-Partialkey", partialKey)
                .header("X-Radiko-User", USER)
                .header("X-Radiko-Device", DEVICE)
                .timeout(Duration.ofSeconds(props.httpReadTimeoutSeconds()))
                .build();
        HttpResponse<String> resp = send(req, "auth2");
        if (resp.statusCode() / 100 != 2) {
            throw new RadikoAuthException("auth2 failed: status=" + resp.statusCode()
                    + " body=" + resp.body());
        }
        // ボディは "{areaId},{areaName},{areaNameAscii}" のCSV形式
        String body = resp.body() == null ? "" : resp.body().trim();
        String[] parts = body.split(",", -1);
        if (parts.length < 2) {
            throw new RadikoAuthException("auth2 response malformed: " + body);
        }
        return new Auth2Result(parts[0].trim(), parts[1].trim());
    }

    static String derivePartialKey(String key, int offset, int length) {
        byte[] keyBytes = key.getBytes(StandardCharsets.US_ASCII);
        if (offset < 0 || length <= 0 || offset + length > keyBytes.length) {
            throw new RadikoAuthException("Invalid key offset/length: offset=" + offset
                    + " length=" + length + " keyLen=" + keyBytes.length);
        }
        byte[] slice = new byte[length];
        System.arraycopy(keyBytes, offset, slice, 0, length);
        return Base64.getEncoder().encodeToString(slice);
    }

    private HttpResponse<String> send(HttpRequest req, String op) {
        try {
            return httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RadikoAuthException(op + " I/O error", e);
        }
    }

    private static String header(HttpResponse<?> resp, String name) {
        return resp.headers().firstValue(name)
                .orElseThrow(() -> new RadikoAuthException("Missing header: " + name));
    }

    /** auth1 レスポンス。テスト用に package-private。 */
    record Auth1Result(String authToken, int keyOffset, int keyLength) {}
    record Auth2Result(String areaId, String areaName) {}

    public static class RadikoAuthException extends RuntimeException {
        public RadikoAuthException(String msg) { super(msg); }
        public RadikoAuthException(String msg, Throwable cause) { super(msg, cause); }
    }

    /** テスト用に内部状態を露出（@VisibleForTesting相当）。 */
    Instant cachedIssuedAtForTest() {
        return cached == null ? null : cached.issuedAt();
    }
}
