package com.personal.medisynth.DTO;

public record AuthResponse(String accessToken, String tokenType, long expiresInMs, UserSummary user) {

    public static AuthResponse bearer(String token, long expiresInMs, UserSummary user) {
        return new AuthResponse(token, "Bearer", expiresInMs, user);
    }
}
