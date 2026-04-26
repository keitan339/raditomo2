package com.raditomo.auth.dto;

public class AuthDtos {

    public record LoginUrlResponse(String authUrl, String state) {}

    public record LoginResponse(String accessToken, long expiresIn, UserResponse user) {}

    public record UserResponse(Long id, String email, String name, String pictureUrl) {}

    private AuthDtos() {}
}
