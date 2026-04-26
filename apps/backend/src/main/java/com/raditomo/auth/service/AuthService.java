package com.raditomo.auth.service;

import com.raditomo.user.entity.User;
import com.raditomo.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 認証フロー本体。
 *
 * - state 検証
 * - Google id_token 取得・検証
 * - 許可リスト（users.is_active=true）チェック
 * - 既存ユーザーの last_login_at 更新、初回はエラー（事前 CLI 登録が前提）
 * - JWT 発行
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final OAuthStateService stateService;
    private final GoogleOAuthService googleOAuthService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final Clock clock;

    public LoginUrl issueLoginUrl() {
        String state = stateService.issue();
        String url = googleOAuthService.buildAuthorizationUrl(state);
        return new LoginUrl(url, state);
    }

    @Transactional
    public LoginResult handleCallback(String code, String state) {
        if (!stateService.consume(state)) {
            throw new InvalidStateException();
        }
        GoogleIdToken idToken = googleOAuthService.exchangeCodeAndVerify(code);
        if (!idToken.emailVerified()) {
            throw new AccessDeniedException("Email not verified");
        }
        User user = userRepository.findByEmail(idToken.email())
                .filter(User::isActive)
                .orElseThrow(() -> new AccessDeniedException("Email not in allowlist: " + idToken.email()));

        // プロフィールの差分は最新で上書き。last_login_at は JST で記録。
        user.setName(idToken.name());
        user.setPictureUrl(idToken.pictureUrl());
        user.setLastLoginAt(OffsetDateTime.now(clock).atZoneSameInstant(ZoneId.of("Asia/Tokyo")).toOffsetDateTime());
        userRepository.save(user);

        JwtService.Issued issued = jwtService.issue(user.getId(), user.getEmail());
        return new LoginResult(issued.token(), issued.expiresInSeconds(), user);
    }

    public record LoginUrl(String authUrl, String state) {}
    public record LoginResult(String accessToken, long expiresInSeconds, User user) {}

    public static class InvalidStateException extends RuntimeException {
        public InvalidStateException() { super("Invalid or expired OAuth state"); }
    }
}
