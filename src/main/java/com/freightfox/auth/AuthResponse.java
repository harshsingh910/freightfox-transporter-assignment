package com.freightfox.auth;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        UserDto user,
        String message
) {
    public static AuthResponse of(String token, long expiresInSeconds, UserDto user, String message) {
        return new AuthResponse(token, "Bearer", expiresInSeconds, user, message);
    }
}
