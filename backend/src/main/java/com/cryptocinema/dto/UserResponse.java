package com.cryptocinema.dto;

import com.cryptocinema.entity.Role;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        Role role
) {
}
