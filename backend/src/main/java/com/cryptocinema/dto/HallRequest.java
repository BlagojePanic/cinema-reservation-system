package com.cryptocinema.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record HallRequest(
        @NotBlank String name,
        @NotNull @Positive Long cinemaId
) {
}
