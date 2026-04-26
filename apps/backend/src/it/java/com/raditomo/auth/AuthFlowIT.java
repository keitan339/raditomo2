package com.raditomo.auth;

import com.raditomo.AbstractIT;
import com.raditomo.user.entity.User;
import com.raditomo.user.repository.UserRepository;
import no.nav.security.mock.oauth2.MockOAuth2Server;
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthFlowIT extends AbstractIT {

    @Autowired TestRestTemplate rest;
    @Autowired UserRepository userRepository;

    static final MockOAuth2Server MOCK_OAUTH = new MockOAuth2Server();
    private static final String ISSUER = "default";

    @DynamicPropertySource
    static void registerOAuthProperties(DynamicPropertyRegistry registry) {
        try {
            MOCK_OAUTH.start();
        } catch (Exception ignored) {
            // already started
        }
        String issuer = ISSUER;
        registry.add("raditomo.oauth.google.client-id", () -> "test-client");
        registry.add("raditomo.oauth.google.client-secret", () -> "test-secret");
        registry.add("raditomo.oauth.google.redirect-uri", () -> "http://localhost/api/auth/google/callback");
        registry.add("raditomo.oauth.google.authorization-uri",
                () -> MOCK_OAUTH.authorizationEndpointUrl(issuer).toString());
        registry.add("raditomo.oauth.google.token-uri",
                () -> MOCK_OAUTH.tokenEndpointUrl(issuer).toString());
        registry.add("raditomo.oauth.google.jwks-uri",
                () -> MOCK_OAUTH.jwksUrl(issuer).toString());
        registry.add("raditomo.oauth.google.issuer",
                () -> MOCK_OAUTH.issuerUrl(issuer).toString());
    }

    @BeforeEach
    void cleanUsers() {
        userRepository.deleteAll();
    }

    @Test
    void loginUrl_returnsAuthUrlAndState() {
        ResponseEntity<Map> resp = rest.getForEntity("/api/auth/google/login-url", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKeys("authUrl", "state");
        String authUrl = (String) resp.getBody().get("authUrl");
        assertThat(authUrl).contains("client_id=test-client");
        assertThat(authUrl).contains("response_type=code");
    }

    @Test
    void callback_issuesJwtForAllowedUser() {
        userRepository.save(User.builder()
                .email("allowed@example.com")
                .active(true)
                .build());
        String state = obtainState();
        enqueueIdToken("allowed@example.com", true, "Allowed User");

        ResponseEntity<Map> resp = rest.getForEntity(
                "/api/auth/google/callback?code=any-code&state=" + state, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKeys("accessToken", "expiresIn", "user");
        Map<String, Object> user = (Map<String, Object>) resp.getBody().get("user");
        assertThat(user.get("email")).isEqualTo("allowed@example.com");
    }

    @Test
    void callback_returns403ForNonAllowlistedEmail() {
        // ユーザーは存在するが is_active=false
        userRepository.save(User.builder()
                .email("disabled@example.com")
                .active(false)
                .build());
        String state = obtainState();
        enqueueIdToken("disabled@example.com", true, "Disabled");

        ResponseEntity<String> resp = rest.getForEntity(
                "/api/auth/google/callback?code=any&state=" + state, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void callback_returns403WhenEmailNotVerified() {
        userRepository.save(User.builder()
                .email("unverified@example.com")
                .active(true)
                .build());
        String state = obtainState();
        enqueueIdToken("unverified@example.com", false, "Unverified");

        ResponseEntity<String> resp = rest.getForEntity(
                "/api/auth/google/callback?code=any&state=" + state, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void callback_returns400ForInvalidState() {
        ResponseEntity<String> resp = rest.getForEntity(
                "/api/auth/google/callback?code=any&state=fabricated-state", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void me_requiresJwt() {
        ResponseEntity<String> resp = rest.getForEntity("/api/auth/me", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String obtainState() {
        ResponseEntity<Map> resp = rest.getForEntity("/api/auth/google/login-url", Map.class);
        return (String) resp.getBody().get("state");
    }

    private void enqueueIdToken(String email, boolean emailVerified, String name) {
        Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("email", email);
        claims.put("email_verified", emailVerified);
        claims.put("name", name);
        claims.put("picture", "https://example.com/pic.png");
        MOCK_OAUTH.enqueueCallback(
                new DefaultOAuth2TokenCallback(
                        "default",
                        "subject-" + email,
                        "JWT",
                        List.of("test-client"),
                        claims,
                        3600L
                )
        );
    }
}
