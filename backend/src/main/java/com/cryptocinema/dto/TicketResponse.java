package com.cryptocinema.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.cryptocinema.entity.TicketStatus;

public record TicketResponse(
        String ticketCode,
        TicketStatus ticketStatus,
        String movieTitle,
        String cinemaName,
        String hallName,
        LocalDateTime screeningStartTime,
        List<String> seats,
        BigDecimal totalAmount,
        Long reservationId,
        LocalDateTime createdAt,
        LocalDateTime usedAt
) {
}
