package com.raditomo.auth.dto;

public class AuthDtos {

    public record LoginUrlResponse(String authUrl, String state) {}

    public record LoginResponse(String accessToken, long expiresIn, UserResponse user) {}

    public record UserResponse(Long id, String email, String name, String pictureUrl) {}

    /**
     * フロントが起動時に取得する認証構成。`idp` で UI のボタン種別を分岐する。
     * googleClientId は idp=google のときだけ非 null（GIS 初期化に必要）。
     */
    public record AuthConfigResponse(String idp, String googleClientId) {}

    public record IdTokenLoginRequest(String idToken) {}

    private AuthDtos() {}
}
