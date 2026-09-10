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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.cryptocinema.dto.MovieRequest;
import com.cryptocinema.dto.MovieResponse;
import com.cryptocinema.service.MovieService;

@RestController
public class MovieController {

    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping("/api/movies")
    public List<MovieResponse> findAll() {
        return movieService.findAll();
    }

    @GetMapping("/api/admin/movies")
    public List<MovieResponse> findAllForAdmin() {
        return movieService.findAllForAdmin();
    }

    @GetMapping("/api/movies/{id}")
    public MovieResponse findById(@PathVariable Long id) {
        return movieService.findById(id);
    }

    @PostMapping("/api/admin/movies")
    @ResponseStatus(HttpStatus.CREATED)
    public MovieResponse create(@Valid @RequestBody MovieRequest request) {
        return movieService.create(request);
    }

    @PutMapping("/api/admin/movies/{id}")
    public MovieResponse update(@PathVariable Long id, @Valid @RequestBody MovieRequest request) {
        return movieService.update(id, request);
    }

    @PostMapping("/api/admin/movies/{id}/poster")
    public MovieResponse uploadPoster(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return movieService.uploadPoster(id, file);
    }

    @PostMapping("/api/admin/movies/{id}/trailer")
    public MovieResponse uploadTrailer(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return movieService.uploadTrailer(id, file);
    }

    @DeleteMapping("/api/admin/movies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        movieService.delete(id);
    }

    @PostMapping("/api/admin/movies/{id}/restore")
    public MovieResponse restore(@PathVariable Long id) {
        return movieService.restore(id);
    }
}
