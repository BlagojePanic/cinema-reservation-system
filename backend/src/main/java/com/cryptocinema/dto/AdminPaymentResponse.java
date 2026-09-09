package com.cryptocinema.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.cryptocinema.entity.PaymentMethod;
import com.cryptocinema.entity.PaymentStatus;

public record AdminPaymentResponse(
        Long paymentId,
        String userEmail,
        String movieTitle,
        Long reservationId,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        PaymentStatus status,
        String reference,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
