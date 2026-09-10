package com.cryptocinema.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.cryptocinema.entity.ReservationStatus;
import com.cryptocinema.entity.TicketStatus;

public record AdminTicketResponse(
        String ticketCode,
        TicketStatus ticketStatus,
        ReservationStatus reservationStatus,
        String movieTitle,
        LocalDateTime screeningStartTime,
        String cinemaName,
        String hallName,
        List<String> seats,
        String userEmail,
        Long reservationId,
        LocalDateTime createdAt,
        LocalDateTime usedAt
) {
}
