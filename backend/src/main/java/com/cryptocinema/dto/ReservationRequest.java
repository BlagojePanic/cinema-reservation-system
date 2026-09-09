package com.cryptocinema.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record ReservationRequest(
        @NotNull Long screeningId,
        @NotEmpty List<Long> screeningSeatIds
) {}
