package com.cryptocinema.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cryptocinema.dto.CinemaRequest;
import com.cryptocinema.dto.CinemaResponse;
import com.cryptocinema.service.CinemaService;

@RestController
public class CinemaController {

    private final CinemaService cinemaService;

    public CinemaController(CinemaService cinemaService) {
        this.cinemaService = cinemaService;
    }

    @GetMapping("/api/cinemas")
    public List<CinemaResponse> findAll() {
        return cinemaService.findAll();
    }

    @GetMapping("/api/cinemas/{id}")
    public CinemaResponse findById(@PathVariable Long id) {
        return cinemaService.findById(id);
    }

    @GetMapping("/api/cities/{cityId}/cinemas")
    public List<CinemaResponse> findByCity(@PathVariable Long cityId) {
        return cinemaService.findByCity(cityId);
    }

    @PostMapping("/api/admin/cinemas")
    @ResponseStatus(HttpStatus.CREATED)
    public CinemaResponse create(@Valid @RequestBody CinemaRequest request) {
        return cinemaService.create(request);
    }

    @PutMapping("/api/admin/cinemas/{id}")
    public CinemaResponse update(@PathVariable Long id, @Valid @RequestBody CinemaRequest request) {
        return cinemaService.update(id, request);
    }

    @DeleteMapping("/api/admin/cinemas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        cinemaService.delete(id);
    }
}
