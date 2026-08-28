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

import com.cryptocinema.dto.HallRequest;
import com.cryptocinema.dto.HallResponse;
import com.cryptocinema.service.HallService;

@RestController
public class HallController {

    private final HallService hallService;

    public HallController(HallService hallService) {
        this.hallService = hallService;
    }

    @GetMapping("/api/cinemas/{cinemaId}/halls")
    public List<HallResponse> findByCinema(@PathVariable Long cinemaId) {
        return hallService.findByCinema(cinemaId);
    }

    @GetMapping("/api/halls/{id}")
    public HallResponse findById(@PathVariable Long id) {
        return hallService.findById(id);
    }

    @PostMapping("/api/admin/halls")
    @ResponseStatus(HttpStatus.CREATED)
    public HallResponse create(@Valid @RequestBody HallRequest request) {
        return hallService.create(request);
    }

    @PutMapping("/api/admin/halls/{id}")
    public HallResponse update(@PathVariable Long id, @Valid @RequestBody HallRequest request) {
        return hallService.update(id, request);
    }

    @DeleteMapping("/api/admin/halls/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        hallService.delete(id);
    }
}
