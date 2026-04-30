package com.raditomo.radiko;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * ラジコ API 用 HTTP レスポンス処理ユーティリティ。
 *
 * <p>radiko CDN は同じエンドポイントでも今日・直近1日先のデータについては
 * gzip で返してくることがある（過去・少し先のデータはキャッシュ済みで plain）。
 * Java の HttpClient は Accept-Encoding を自動付与せず、また自動解凍も行わないため、
 * gzip マジックバイトと Content-Encoding を見て手動で解凍する必要がある。
 *
 * <p>過去に文字列受信 ({@code BodyHandlers.ofString}) で受け取り、UTF-8 デコード時に
 * 非 UTF-8 バイト ({@code 0x8b} 等) が U+FFFD に置換されて gzip ストリームが壊れ、
 * パース失敗 → 番組表が空、というバグがあったため、必ずバイトで受信してこの
 * メソッドで復号すること。
 */
public final class RadikoHttp {

    private RadikoHttp() {}

    /**
     * バイト列のレスポンスボディを UTF-8 文字列に復元する。
     * gzip 圧縮されている場合は自動で解凍する。
     */
    public static String decodeBody(HttpResponse<byte[]> resp) throws IOException {
        byte[] body = resp.body();
        String encoding = resp.headers().firstValue("Content-Encoding").orElse("");
        boolean isGzip = "gzip".equalsIgnoreCase(encoding)
                || (body.length >= 2 && body[0] == 0x1f && body[1] == (byte) 0x8b);
        if (isGzip) {
            try (var gz = new GZIPInputStream(new ByteArrayInputStream(body))) {
                body = gz.readAllBytes();
            }
        }
        return new String(body, StandardCharsets.UTF_8);
    }
}
