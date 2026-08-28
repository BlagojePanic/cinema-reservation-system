package com.cryptocinema.dto;

public record SeatResponse(
        Long id,
        String rowLabel,
        int seatNumber,
        Long hallId
) {
}
