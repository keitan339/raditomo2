package com.raditomo.radiko;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.SSLSession;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Java 標準 {@link java.net.http.HttpClient} は Accept-Encoding を自動付与せず、
 * gzip も自動解凍しない。radiko CDN は最近の番組表だけ gzip で返す挙動があり、
 * String 受信＋ UTF-8 デコードでは {@code 0x8b} 等が U+FFFD に置換されて
 * gzip ストリームが破壊される。{@link RadikoHttp#decodeBody} はこの境界を吸収する。
 *
 * 本テストは:
 *   - Content-Encoding: gzip + 圧縮ボディ → 解凍されること
 *   - Content-Encoding ヘッダ無し + 圧縮ボディ → マジックバイト判定で解凍されること
 *   - 非圧縮 plain ボディ → そのまま UTF-8 文字列になること
 * を保証する。HttpClient のデフォルト挙動が将来変わっても、このテストが先に落ちる。
 */
class RadikoHttpTest {

    @Test
    void decodesGzippedBody_whenContentEncodingHeaderSet() throws Exception {
        String original = "<programs><prog>テスト番組</prog></programs>";
        byte[] gzipped = gzip(original);

        String decoded = RadikoHttp.decodeBody(stub(gzipped, "gzip"));

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void decodesGzippedBody_whenContentEncodingHeaderMissing_butMagicBytesPresent() throws Exception {
        // radiko CDN は gzip で返すのに Content-Encoding ヘッダを付けないケースがある。
        // マジックバイト (1f 8b) で判定する経路の確認。
        String original = "<stations><id>TBS</id></stations>";
        byte[] gzipped = gzip(original);

        String decoded = RadikoHttp.decodeBody(stub(gzipped, null));

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void leavesPlainBodyUnchanged() throws Exception {
        String original = "<plain>UTF-8 日本語</plain>";
        byte[] plain = original.getBytes(StandardCharsets.UTF_8);

        String decoded = RadikoHttp.decodeBody(stub(plain, null));

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void handlesEmptyBody() throws Exception {
        String decoded = RadikoHttp.decodeBody(stub(new byte[0], null));
        assertThat(decoded).isEmpty();
    }

    private static byte[] gzip(String s) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(buf)) {
            gz.write(s.getBytes(StandardCharsets.UTF_8));
        }
        return buf.toByteArray();
    }

    private static HttpResponse<byte[]> stub(byte[] body, String contentEncoding) {
        Map<String, List<String>> headerMap = contentEncoding == null
                ? Map.of()
                : Map.of("Content-Encoding", List.of(contentEncoding));
        HttpHeaders headers = HttpHeaders.of(headerMap, (k, v) -> true);
        return new HttpResponse<>() {
            @Override public int statusCode() { return 200; }
            @Override public HttpRequest request() { return null; }
            @Override public Optional<HttpResponse<byte[]>> previousResponse() { return Optional.empty(); }
            @Override public HttpHeaders headers() { return headers; }
            @Override public byte[] body() { return body; }
            @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
            @Override public URI uri() { return URI.create("https://example.test/"); }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
}
