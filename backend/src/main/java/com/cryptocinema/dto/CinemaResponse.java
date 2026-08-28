package com.cryptocinema.dto;

public record CinemaResponse(
        Long id,
        String name,
        String address,
        CityResponse city
) {
}
