package com.cryptocinema.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cryptocinema.dto.AdminReservationResponse;
import com.cryptocinema.service.ReservationService;

@RestController
@RequestMapping("/api/admin/reservations")
public class AdminReservationController {

    private final ReservationService reservationService;

    public AdminReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping
    public List<AdminReservationResponse> findAll() {
        return reservationService.findAllForAdmin();
    }
}
