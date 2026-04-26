package com.raditomo.auth.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OAuth state（CSRF防止）の発行と検証。
 *
 * 個人利用のため in-memory のシンプルな実装。state は 5 分で失効。
 * 検証は1回限り（消費）。
 */
@Service
public class OAuthStateService {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Clock clock;
    private final Map<String, Instant> states = new ConcurrentHashMap<>();

    public OAuthStateService(Clock clock) {
        this.clock = clock;
    }

    public String issue() {
        purgeExpired();
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        states.put(state, clock.instant().plus(TTL));
        return state;
    }

    public boolean consume(String state) {
        if (state == null) return false;
        Instant expiresAt = states.remove(state);
        return expiresAt != null && clock.instant().isBefore(expiresAt);
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        states.entrySet().removeIf(e -> !now.isBefore(e.getValue()));
    }
}
