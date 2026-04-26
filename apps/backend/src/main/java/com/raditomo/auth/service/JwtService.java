package com.raditomo.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 発行・検証。HS256、有効期限のみで管理（リフレッシュトークン・ブラックリストなし）。
 * 設計判断: アクセストークン 24h、リフレッシュトークンは発行しない（毎回認証ポリシー）。
 */
@Service
@Slf4j
public class JwtService {

    private static final String CLAIM_EMAIL = "email";

    private final SecretKey signingKey;
    private final Duration expiration;
    private final Clock clock;

    public JwtService(
            @Value("${raditomo.jwt.secret}") String secret,
            @Value("${raditomo.jwt.expiration-hours:24}") long expirationHours,
            Clock clock
    ) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "raditomo.jwt.secret must be at least 32 bytes (256 bits) for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.expiration = Duration.ofHours(expirationHours);
        this.clock = clock;
    }

    public Issued issue(long userId, String email) {
        Instant now = clock.instant();
        Instant exp = now.plus(expiration);
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        return new Issued(token, expiration.toSeconds());
    }

    public Verified verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        long userId = Long.parseLong(claims.getSubject());
        String email = claims.get(CLAIM_EMAIL, String.class);
        return new Verified(userId, email);
    }

    public record Issued(String token, long expiresInSeconds) {}
    public record Verified(long userId, String email) {}
}
