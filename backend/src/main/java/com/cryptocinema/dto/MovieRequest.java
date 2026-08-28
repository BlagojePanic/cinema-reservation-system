package com.cryptocinema.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record MovieRequest(
        @NotBlank String title,
        @NotBlank String description,
        @NotBlank String genre,
        @Positive int durationMinutes,
        String ageRating,
        String director,
        LocalDate releaseDate,
        String posterUrl,
        String trailerUrl
) {
}
