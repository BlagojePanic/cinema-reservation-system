package com.cryptocinema.dto;

import jakarta.validation.constraints.Positive;

public record SeatGenerationRequest(
        @Positive int rows,
        @Positive int seatsPerRow
) {
}
