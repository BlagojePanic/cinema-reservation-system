package com.cryptocinema.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cryptocinema.dto.ScreeningRequest;
import com.cryptocinema.dto.ScreeningResponse;
import com.cryptocinema.dto.ScreeningSeatResponse;
import com.cryptocinema.service.ScreeningSeatService;
import com.cryptocinema.service.ScreeningService;

import org.springframework.security.core.Authentication;

@RestController
public class ScreeningController {

    private final ScreeningService screeningService;
    private final ScreeningSeatService screeningSeatService;

    public ScreeningController(ScreeningService screeningService, ScreeningSeatService screeningSeatService) {
        this.screeningService = screeningService;
        this.screeningSeatService = screeningSeatService;
    }

    @GetMapping("/api/screenings")
    public List<ScreeningResponse> findFiltered(
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) Long cinemaId,
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return screeningService.findFiltered(cityId, cinemaId, movieId, date);
    }

    @GetMapping("/api/screenings/{id}")
    public ScreeningResponse findById(@PathVariable Long id) {
        return screeningService.findById(id);
    }

    @GetMapping("/api/movies/{movieId}/screenings")
    public List<ScreeningResponse> findByMovie(@PathVariable Long movieId) {
        return screeningService.findByMovie(movieId);
    }

    @GetMapping("/api/halls/{hallId}/screenings")
    public List<ScreeningResponse> findByHall(@PathVariable Long hallId) {
        return screeningService.findByHall(hallId);
    }

    @GetMapping("/api/screenings/{screeningId}/seats")
    public List<ScreeningSeatResponse> findSeats(
            @PathVariable Long screeningId,
            Authentication authentication
    ) {
        return screeningSeatService.findByScreening(screeningId, authentication);
    }

    @PostMapping("/api/screenings/{screeningId}/seats/{screeningSeatId}/hold")
    public ScreeningSeatResponse holdSeat(
            @PathVariable Long screeningId,
            @PathVariable Long screeningSeatId,
            Authentication authentication
    ) {
        return screeningSeatService.hold(screeningId, screeningSeatId, authentication);
    }

    @DeleteMapping("/api/screenings/{screeningId}/seats/{screeningSeatId}/hold")
    public ScreeningSeatResponse releaseSeat(
            @PathVariable Long screeningId,
            @PathVariable Long screeningSeatId,
            Authentication authentication
    ) {
        return screeningSeatService.release(screeningId, screeningSeatId, authentication);
    }

    @PostMapping("/api/admin/screenings")
    @ResponseStatus(HttpStatus.CREATED)
    public ScreeningResponse create(@Valid @RequestBody ScreeningRequest request) {
        return screeningService.create(request);
    }

    @PutMapping("/api/admin/screenings/{id}")
    public ScreeningResponse update(@PathVariable Long id, @Valid @RequestBody ScreeningRequest request) {
        return screeningService.update(id, request);
    }

    @DeleteMapping("/api/admin/screenings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        screeningService.delete(id);
    }
}
