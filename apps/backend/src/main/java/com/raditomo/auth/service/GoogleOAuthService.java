package com.raditomo.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Google OAuth 2.0 / OIDC 連携。
 *
 * - 認可URLの組み立て（state は呼び出し側で用意）
 * - 認可コードと ID トークンの交換
 * - ID トークンの署名検証（JWKS）と claim 抽出
 *
 * 設計判断: Spring Security の OAuth2 Client 自動構成は session ベースで JWT 方針と
 * 衝突するため使わず、薄い手動フローを書いている。ただし JWKS 検証は
 * NimbusJwtDecoder（spring-security-oauth2-jose）に委譲する。
 */
@Service
@Slf4j
public class GoogleOAuthService {

    private final GoogleOAuthProperties props;
    private final RestClient restClient;
    private final Map<String, JwtDecoder> jwtDecoderCache = new ConcurrentHashMap<>();

    public GoogleOAuthService(GoogleOAuthProperties props, RestClient restClient) {
        this.props = props;
        this.restClient = restClient;
    }

    public String buildAuthorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString(props.authorizationUri())
                .queryParam("client_id", props.clientId())
                .queryParam("redirect_uri", props.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("access_type", "online")
                .queryParam("prompt", "select_account")
                .encode()
                .toUriString();
    }

    public GoogleIdToken exchangeCodeAndVerify(String code) {
        String idToken = exchangeCode(code);
        return verifyAndExtract(idToken);
    }

    /**
     * GIS（Google Identity Services）から直接渡された ID Token を検証する。
     * 認可コードフローを介さない（Google ボタンで取得した id_token を POST する経路）。
     */
    public GoogleIdToken verifyIdToken(String idToken) {
        return verifyAndExtract(idToken);
    }

    /** 設定値の authorization-uri / issuer から IdP 種別を推測する（フロントの分岐用）。 */
    public IdpKind detectIdp() {
        String uri = props.authorizationUri() == null ? "" : props.authorizationUri();
        String issuer = props.issuer() == null ? "" : props.issuer();
        if (uri.contains("accounts.google.com") || issuer.contains("accounts.google.com")) {
            return IdpKind.GOOGLE;
        }
        if (uri.contains("/realms/") || issuer.contains("/realms/")) {
            return IdpKind.KEYCLOAK;
        }
        return IdpKind.CUSTOM;
    }

    public String clientId() {
        return props.clientId();
    }

    public enum IdpKind { GOOGLE, KEYCLOAK, CUSTOM }

    private String exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());
        form.add("redirect_uri", props.redirectUri());
        form.add("grant_type", "authorization_code");

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = restClient.post()
                .uri(props.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(Map.class);

        if (resp == null || !resp.containsKey("id_token")) {
            throw new IllegalStateException("Google token endpoint returned no id_token");
        }
        return (String) resp.get("id_token");
    }

    private GoogleIdToken verifyAndExtract(String idToken) {
        JwtDecoder decoder = jwtDecoderCache.computeIfAbsent(
                props.jwksUri(),
                uri -> NimbusJwtDecoder.withJwkSetUri(uri).build());

        Jwt jwt = decoder.decode(idToken);
        String iss = jwt.getIssuer() != null ? jwt.getIssuer().toString() : null;
        if (!matchesIssuer(iss)) {
            throw new IllegalStateException("Unexpected id_token issuer: " + iss);
        }
        if (!props.clientId().equals(jwt.getAudience().stream().findFirst().orElse(null))) {
            throw new IllegalStateException("Unexpected id_token audience");
        }

        Map<String, Object> claims = new HashMap<>(jwt.getClaims());
        String email = (String) claims.get("email");
        Boolean emailVerified = (Boolean) claims.getOrDefault("email_verified", Boolean.FALSE);
        String name = (String) claims.get("name");
        String picture = (String) claims.get("picture");
        return new GoogleIdToken(email, Boolean.TRUE.equals(emailVerified), name, picture);
    }

    private boolean matchesIssuer(String iss) {
        if (iss == null) return false;
        // Google は "https://accounts.google.com" もしくは "accounts.google.com"。
        // mock-oauth2-server は固有 issuer を返すので一致チェックは設定値ベース。
        String expected = props.issuer();
        return iss.equals(expected) || iss.equals(expected.replaceFirst("^https?://", ""));
    }
}
