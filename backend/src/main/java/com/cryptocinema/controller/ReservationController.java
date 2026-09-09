package com.cryptocinema.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cryptocinema.dto.ReservationRequest;
import com.cryptocinema.dto.ReservationResponse;
import com.cryptocinema.service.ReservationService;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ReservationResponse create(
            @Valid @RequestBody ReservationRequest request,
            Authentication authentication
    ) {
        return reservationService.create(request, authentication);
    }

    @GetMapping("/me")
    public List<ReservationResponse> findMine(Authentication authentication) {
        return reservationService.findMine(authentication);
    }

    @GetMapping("/{id}")
    public ReservationResponse findById(@PathVariable Long id, Authentication authentication) {
        return reservationService.findById(id, authentication);
    }

    @PostMapping("/{id}/cancel")
    public ReservationResponse cancel(@PathVariable Long id, Authentication authentication) {
        return reservationService.cancel(id, authentication);
    }
}
