package com.cryptocinema.dto;

import java.time.LocalDate;

public record MovieResponse(
        Long id,
        String title,
        String description,
        String genre,
        int durationMinutes,
        String ageRating,
        String director,
        LocalDate releaseDate,
        String posterUrl,
        String trailerUrl
) {
}
