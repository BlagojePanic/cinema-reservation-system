package com.cryptocinema.dto;

public record HallResponse(
        Long id,
        String name,
        Long cinemaId,
        String cinemaName
) {
}
