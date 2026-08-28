package com.cryptocinema.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SeatRequest(
        @NotBlank String rowLabel,
        @Positive int seatNumber,
        @NotNull @Positive Long hallId
) {
}
