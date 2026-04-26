package com.raditomo.radiko.auth;

import com.raditomo.radiko.RadikoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.BiPredicate;

import static org.assertj.core.api.Assertions.*;

class RadikoAuthServiceTest {

    private static final String AUTH_KEY = "0123456789abcdef0123456789abcdef01234567"; // 40 chars
    private RadikoProperties props;
    private FakeHttpClient http;
    private MutableClock clock;
    private RadikoAuthService service;

    @BeforeEach
    void setup() {
        props = new RadikoProperties(8, "https://radiko.jp", AUTH_KEY, 3, 10, 30, 3);
        http = new FakeHttpClient();
        clock = new MutableClock(Instant.parse("2026-04-27T00:00:00Z"));
        service = new RadikoAuthService(props, http, clock);
    }

    @Test
    void derivePartialKey_slicesAndBase64() {
        // offset=2, length=4 → "23456789abcdef..." の先頭 4 文字を切り取る … 実際は ASCII で計算される
        // "0123456789abcdef..."[2..6] = "2345"
        String b64 = RadikoAuthService.derivePartialKey(AUTH_KEY, 2, 4);
        assertThat(new String(Base64.getDecoder().decode(b64), StandardCharsets.US_ASCII)).isEqualTo("2345");
    }

    @Test
    void derivePartialKey_throwsOnOutOfRange() {
        assertThatThrownBy(() -> RadikoAuthService.derivePartialKey(AUTH_KEY, 100, 10))
                .isInstanceOf(RadikoAuthService.RadikoAuthException.class);
    }

    @Test
    void getToken_callsAuth1Auth2_andCachesResult() throws Exception {
        http.queueAuth1Response("token-abc", 5, 10);
        http.queueAuth2Response("JP13", "東京都");

        RadikoAuthToken token = service.getToken();
        assertThat(token.authToken()).isEqualTo("token-abc");
        assertThat(token.areaId()).isEqualTo("JP13");
        assertThat(token.areaName()).isEqualTo("東京都");
        assertThat(http.requestCount()).isEqualTo(2);

        // 2回目はキャッシュヒット → 追加リクエストなし
        RadikoAuthToken token2 = service.getToken();
        assertThat(token2).isEqualTo(token);
        assertThat(http.requestCount()).isEqualTo(2);
    }

    @Test
    void getToken_refetchesAfterCacheExpiry() throws Exception {
        http.queueAuth1Response("token-1", 0, 8);
        http.queueAuth2Response("JP13", "東京都");
        service.getToken();

        // キャッシュ TTL（3h）超過させる
        clock.advance(Duration.ofHours(4));

        http.queueAuth1Response("token-2", 0, 8);
        http.queueAuth2Response("JP13", "東京都");
        RadikoAuthToken refreshed = service.getToken();
        assertThat(refreshed.authToken()).isEqualTo("token-2");
        assertThat(http.requestCount()).isEqualTo(4);
    }

    @Test
    void getToken_throwsWhenAuth1Returns500() {
        http.queueResponse(500, "", Map.of());
        assertThatThrownBy(() -> service.getToken())
                .isInstanceOf(RadikoAuthService.RadikoAuthException.class)
                .hasMessageContaining("auth1");
    }

    // --- helpers ---

    static class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant initial) { this.now = initial; }
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    /** HttpClient のスタブ。queue した順に返す。 */
    static class FakeHttpClient extends HttpClient {
        private final Deque<StubResponse> queue = new ArrayDeque<>();
        private int requestCount = 0;

        int requestCount() { return requestCount; }

        void queueAuth1Response(String authToken, int keyOffset, int keyLength) {
            queueResponse(200, "", Map.of(
                    "X-Radiko-AuthToken", authToken,
                    "X-Radiko-KeyOffset", String.valueOf(keyOffset),
                    "X-Radiko-KeyLength", String.valueOf(keyLength)
            ));
        }

        void queueAuth2Response(String areaId, String areaName) {
            queueResponse(200, areaId + "," + areaName + "," + areaName, Map.of());
        }

        void queueResponse(int status, String body, Map<String, String> headers) {
            queue.add(new StubResponse(status, body, headers));
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) throws IOException {
            requestCount++;
            if (queue.isEmpty()) throw new IOException("No stub response queued");
            StubResponse stub = queue.removeFirst();
            return (HttpResponse<T>) new SimpleHttpResponse(request, stub);
        }

        @Override public java.net.http.HttpClient.Version version() { return Version.HTTP_1_1; }
        @Override public java.net.http.HttpClient.Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<java.net.ProxySelector> proxy() { return Optional.empty(); }
        @Override public javax.net.ssl.SSLContext sslContext() { return null; }
        @Override public javax.net.ssl.SSLParameters sslParameters() { return null; }
        @Override public Optional<java.net.Authenticator> authenticator() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Optional<java.util.concurrent.Executor> executor() { return Optional.empty(); }
        @Override public Optional<java.net.CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) {
            return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, bodyHandler);
        }
    }

    record StubResponse(int status, String body, Map<String, String> headers) {}

    static class SimpleHttpResponse implements HttpResponse<String> {
        private final HttpRequest req;
        private final StubResponse stub;
        SimpleHttpResponse(HttpRequest req, StubResponse stub) { this.req = req; this.stub = stub; }
        @Override public int statusCode() { return stub.status(); }
        @Override public HttpRequest request() { return req; }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() {
            Map<String, List<String>> map = new HashMap<>();
            stub.headers().forEach((k, v) -> map.put(k, List.of(v)));
            BiPredicate<String, String> alwaysAccept = (a, b) -> true;
            return HttpHeaders.of(map, alwaysAccept);
        }
        @Override public String body() { return stub.body(); }
        @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
        @Override public java.net.URI uri() { return req.uri(); }
        @Override public java.net.http.HttpClient.Version version() { return java.net.http.HttpClient.Version.HTTP_1_1; }
    }
}
