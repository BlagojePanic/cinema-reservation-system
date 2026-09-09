package com.cryptocinema.dto;

import jakarta.validation.constraints.NotNull;

import com.cryptocinema.entity.PaymentMethod;

public record PaymentRequest(
        @NotNull PaymentMethod method,
        boolean simulateSuccess
) {
}
