package com.raditomo.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

/**
 * OAuth state（CSRF防止）の発行と検証。
 *
 * 設計判断: HMAC-SHA256 で署名した自己完結型トークン（payload + 署名）。
 * バックエンドのプロセス再起動にも耐えるため、メモリには保持しない。
 *
 * トークン構造: base64url(rand) "." base64url(expiresEpochSec) "." base64url(hmac)
 * - rand: 16 バイトのランダム（同一セッション内の衝突回避）
 * - expiresEpochSec: 失効時刻（秒）
 * - hmac: HMAC-SHA256(secret, rand "." expiresEpochSec)
 *
 * トレードオフ: 単発消費 (single-use) は強制しないため、TTL 内であれば理論上 replay は可能。
 * 個人利用＆ TTL 5 分で許容範囲とする。
 */
@Service
@Slf4j
public class OAuthStateService {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final Clock clock;
    private final byte[] secret;

    public OAuthStateService(Clock clock,
                             @Value("${raditomo.jwt.secret}") String secret) {
        this.clock = clock;
        // JWT 用の秘密鍵を流用（同じプロセスの "サーバー秘密"）。
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue() {
        byte[] rand = new byte[16];
        RANDOM.nextBytes(rand);
        long expiresAt = clock.instant().plus(TTL).getEpochSecond();
        String payload = URL_ENCODER.encodeToString(rand) + "." + expiresAt;
        String mac = URL_ENCODER.encodeToString(hmac(payload));
        return payload + "." + mac;
    }

    public boolean consume(String token) {
        if (token == null || token.isEmpty()) return false;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return false;
        String payload = parts[0] + "." + parts[1];
        byte[] expectedMac = hmac(payload);
        byte[] givenMac;
        try {
            givenMac = URL_DECODER.decode(parts[2]);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!MessageDigest.isEqual(expectedMac, givenMac)) {
            log.debug("State HMAC mismatch");
            return false;
        }
        long expiresAt;
        try {
            expiresAt = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (clock.instant().getEpochSecond() >= expiresAt) {
            log.debug("State expired");
            return false;
        }
        return true;
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }
}
