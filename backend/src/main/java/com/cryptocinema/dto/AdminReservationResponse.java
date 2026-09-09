package com.cryptocinema.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.cryptocinema.entity.PaymentMethod;
import com.cryptocinema.entity.PaymentStatus;
import com.cryptocinema.entity.ReservationStatus;

public record AdminReservationResponse(
        Long reservationId,
        Long userId,
        String userEmail,
        String movieTitle,
        Long screeningId,
        LocalDateTime startTime,
        String cinemaName,
        String hallName,
        List<String> seats,
        BigDecimal totalAmount,
        ReservationStatus reservationStatus,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        PaymentStatus latestPaymentStatus,
        PaymentMethod latestPaymentMethod,
        String latestPaymentReference,
        String latestPaymentTransactionHash
) {
}
