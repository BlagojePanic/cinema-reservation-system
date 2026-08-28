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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cryptocinema.dto.CityRequest;
import com.cryptocinema.dto.CityResponse;
import com.cryptocinema.service.CityService;

@RestController
public class CityController {

    private final CityService cityService;

    public CityController(CityService cityService) {
        this.cityService = cityService;
    }

    @GetMapping("/api/cities")
    public List<CityResponse> findAll() {
        return cityService.findAll();
    }

    @GetMapping("/api/cities/{id}")
    public CityResponse findById(@PathVariable Long id) {
        return cityService.findById(id);
    }

    @PostMapping("/api/admin/cities")
    @ResponseStatus(HttpStatus.CREATED)
    public CityResponse create(@Valid @RequestBody CityRequest request) {
        return cityService.create(request);
    }

    @PutMapping("/api/admin/cities/{id}")
    public CityResponse update(@PathVariable Long id, @Valid @RequestBody CityRequest request) {
        return cityService.update(id, request);
    }

    @DeleteMapping("/api/admin/cities/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        cityService.delete(id);
    }
}
