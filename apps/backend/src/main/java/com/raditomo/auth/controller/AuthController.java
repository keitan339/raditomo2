package com.raditomo.auth.controller;

import com.raditomo.auth.dto.AuthDtos.LoginResponse;
import com.raditomo.auth.dto.AuthDtos.LoginUrlResponse;
import com.raditomo.auth.dto.AuthDtos.UserResponse;
import com.raditomo.auth.service.AuthService;
import com.raditomo.user.entity.User;
import com.raditomo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @GetMapping("/google/login-url")
    public LoginUrlResponse loginUrl() {
        AuthService.LoginUrl url = authService.issueLoginUrl();
        return new LoginUrlResponse(url.authUrl(), url.state());
    }

    @GetMapping("/google/callback")
    public LoginResponse callback(@RequestParam String code, @RequestParam String state) {
        AuthService.LoginResult result = authService.handleCallback(code, state);
        User u = result.user();
        return new LoginResponse(
                result.accessToken(),
                result.expiresInSeconds(),
                new UserResponse(u.getId(), u.getEmail(), u.getName(), u.getPictureUrl()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        // JWT はステートレス。クライアント側で SessionStorage から破棄。
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Long userId) {
        if (userId == null) {
            throw new AccessDeniedException("Not authenticated");
        }
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new AccessDeniedException("User not found"));
        return new UserResponse(u.getId(), u.getEmail(), u.getName(), u.getPictureUrl());
    }

    @ExceptionHandler(AuthService.InvalidStateException.class)
    public ResponseEntity<String> invalidState(AuthService.InvalidStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> accessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }
}
