package com.cryptocinema.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cryptocinema.dto.SeatGenerationRequest;
import com.cryptocinema.dto.SeatRequest;
import com.cryptocinema.dto.SeatResponse;
import com.cryptocinema.service.SeatService;

@RestController
public class SeatController {

    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    @GetMapping("/api/halls/{hallId}/seats")
    public List<SeatResponse> findByHall(@PathVariable Long hallId) {
        return seatService.findByHall(hallId);
    }

    @PostMapping("/api/admin/seats")
    @ResponseStatus(HttpStatus.CREATED)
    public SeatResponse create(@Valid @RequestBody SeatRequest request) {
        return seatService.create(request);
    }

    @PostMapping("/api/admin/halls/{hallId}/seats/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public List<SeatResponse> generate(
            @PathVariable Long hallId,
            @Valid @RequestBody SeatGenerationRequest request
    ) {
        return seatService.generate(hallId, request);
    }

    @DeleteMapping("/api/admin/seats/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        seatService.delete(id);
    }
}
