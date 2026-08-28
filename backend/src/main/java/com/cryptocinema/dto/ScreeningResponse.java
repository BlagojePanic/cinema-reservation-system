package com.cryptocinema.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ScreeningResponse(
        Long id,
        Long movieId,
        String movieTitle,
        Long hallId,
        String hallName,
        Long cinemaId,
        String cinemaName,
        Long cityId,
        String cityName,
        LocalDateTime startTime,
        BigDecimal ticketPrice
) {}
