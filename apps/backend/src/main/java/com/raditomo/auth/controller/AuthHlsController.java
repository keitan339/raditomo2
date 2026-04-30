package com.raditomo.auth.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nginx の auth_request サブリクエストを処理し、HLS 配信のアクセス制御を行う。
 *
 * Nginx は以下のように呼び出す:
 *   internal location /internal/auth-hls;
 *   proxy_set_header  X-Original-URI $request_uri;
 *   proxy_set_header  Authorization  $http_authorization;
 *
 * - JWT が有効で、URI のユーザーID部分が JWT の sub と一致 → 200
 * - JWT 無効・欠落 → 401
 * - JWT は有効だがパスのユーザーIDが一致しない → 403
 *
 * パス形式: /hls/{userId}/{title}/{date}/playlist.m3u8 など。
 */
@RestController
@RequestMapping("/api/internal")
@Slf4j
public class AuthHlsController {

    private static final Pattern HLS_PATH = Pattern.compile("^/hls/(\\d+)/.+");

    @GetMapping("/auth-hls")
    public ResponseEntity<Void> authHls(
            @AuthenticationPrincipal Long userId,
            @RequestHeader(value = "X-Original-URI", required = false) String originalUri) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (originalUri == null || originalUri.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String path;
        try {
            path = new URI(originalUri).getPath();
        } catch (URISyntaxException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Matcher m = HLS_PATH.matcher(path);
        if (!m.matches()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        long pathUserId;
        try {
            pathUserId = Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (pathUserId != userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok().build();
    }
}
