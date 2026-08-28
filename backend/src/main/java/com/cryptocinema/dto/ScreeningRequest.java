package com.cryptocinema.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ScreeningRequest(
        @NotNull Long movieId,
        @NotNull Long hallId,
        @NotNull LocalDateTime startTime,
        @NotNull @Positive BigDecimal ticketPrice
) {}
