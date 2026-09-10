package com.cryptocinema.dto;

public record TicketValidationResponse(
        boolean accepted,
        String message,
        AdminTicketResponse ticket
) {
}
