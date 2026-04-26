package com.raditomo.auth.service;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "test_secret_key_must_be_at_least_32_bytes_long_to_satisfy_hs256_requirement";
    private MutableClock clock;
    private JwtService service;

    @BeforeEach
    void setup() {
        clock = new MutableClock(Instant.parse("2026-04-27T00:00:00Z"));
        service = new JwtService(SECRET, 24, clock);
    }

    @Test
    void issue_returnsTokenAndExpiresInSeconds() {
        JwtService.Issued issued = service.issue(42L, "user@example.com");
        assertThat(issued.token()).isNotBlank();
        assertThat(issued.expiresInSeconds()).isEqualTo(24 * 60 * 60);
    }

    @Test
    void verify_returnsClaimsForValidToken() {
        JwtService.Issued issued = service.issue(42L, "user@example.com");
        JwtService.Verified verified = service.verify(issued.token());
        assertThat(verified.userId()).isEqualTo(42L);
        assertThat(verified.email()).isEqualTo("user@example.com");
    }

    @Test
    void verify_throwsForTamperedToken() {
        JwtService.Issued issued = service.issue(42L, "user@example.com");
        String tampered = issued.token().substring(0, issued.token().length() - 4) + "AAAA";
        assertThatThrownBy(() -> service.verify(tampered)).isInstanceOf(SignatureException.class);
    }

    @Test
    void verify_throwsForExpiredToken() {
        JwtService.Issued issued = service.issue(42L, "user@example.com");
        clock.advance(Duration.ofHours(25));
        assertThatThrownBy(() -> service.verify(issued.token())).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void constructor_throwsForShortSecret() {
        assertThatThrownBy(() -> new JwtService("too_short", 24, clock))
                .isInstanceOf(IllegalStateException.class);
    }

    static class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant initial) { this.now = initial; }
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
