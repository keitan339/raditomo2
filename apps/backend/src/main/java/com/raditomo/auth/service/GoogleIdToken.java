package com.raditomo.auth.service;

/**
 * Google ID トークンから抽出したユーザー情報。
 */
public record GoogleIdToken(
        String email,
        boolean emailVerified,
        String name,
        String pictureUrl
) {
}
