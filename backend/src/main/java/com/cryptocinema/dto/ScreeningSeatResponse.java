package com.cryptocinema.dto;

import java.time.LocalDateTime;

import com.cryptocinema.entity.ScreeningSeatStatus;

public record ScreeningSeatResponse(
        Long screeningSeatId,
        Long seatId,
        String rowLabel,
        int seatNumber,
        ScreeningSeatStatus status,
        boolean heldByCurrentUser,
        LocalDateTime holdExpiresAt
) {}
