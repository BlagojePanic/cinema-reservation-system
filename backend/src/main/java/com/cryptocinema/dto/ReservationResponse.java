package com.cryptocinema.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.cryptocinema.entity.ReservationStatus;

public record ReservationResponse(
        Long reservationId,
        ReservationStatus status,
        String movieTitle,
        Long screeningId,
        LocalDateTime screeningStartTime,
        String cinemaName,
        String hallName,
        List<String> seatLabels,
        BigDecimal ticketPrice,
        BigDecimal totalAmount,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {}
