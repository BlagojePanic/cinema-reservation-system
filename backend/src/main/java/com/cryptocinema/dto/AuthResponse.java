package com.cryptocinema.dto;

public record AuthResponse(
        String token,
        String tokenType,
        UserResponse user
) {
}
