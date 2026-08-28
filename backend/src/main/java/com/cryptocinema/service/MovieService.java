package com.cryptocinema.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cryptocinema.dto.MovieRequest;
import com.cryptocinema.dto.MovieResponse;
import com.cryptocinema.entity.Movie;
import com.cryptocinema.exception.ConflictException;
import com.cryptocinema.exception.ResourceNotFoundException;
import com.cryptocinema.repository.MovieRepository;
import com.cryptocinema.repository.ScreeningRepository;

@Service
public class MovieService {

    private final MovieRepository movieRepository;
    private final ScreeningRepository screeningRepository;

    public MovieService(MovieRepository movieRepository, ScreeningRepository screeningRepository) {
        this.movieRepository = movieRepository;
        this.screeningRepository = screeningRepository;
    }

    @Transactional(readOnly = true)
    public List<MovieResponse> findAll() {
        return movieRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MovieResponse findById(Long id) {
        return toResponse(getMovie(id));
    }

    @Transactional
    public MovieResponse create(MovieRequest request) {
        Movie movie = new Movie();
        applyRequest(movie, request);
        return toResponse(movieRepository.save(movie));
    }

    @Transactional
    public MovieResponse update(Long id, MovieRequest request) {
        Movie movie = getMovie(id);
        applyRequest(movie, request);
        return toResponse(movie);
    }

    @Transactional
    public void delete(Long id) {
        Movie movie = getMovie(id);
        if (screeningRepository.existsByMovieId(id)) {
            throw new ConflictException("Movie cannot be deleted while it has screenings");
        }
        movieRepository.delete(movie);
    }

    private Movie getMovie(Long id) {
        return movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found"));
    }

    private void applyRequest(Movie movie, MovieRequest request) {
        movie.setTitle(request.title().trim());
        movie.setDescription(request.description().trim());
        movie.setGenre(request.genre().trim());
        movie.setDurationMinutes(request.durationMinutes());
        movie.setAgeRating(trimToNull(request.ageRating()));
        movie.setDirector(trimToNull(request.director()));
        movie.setReleaseDate(request.releaseDate());
        movie.setPosterUrl(trimToNull(request.posterUrl()));
        movie.setTrailerUrl(trimToNull(request.trailerUrl()));
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private MovieResponse toResponse(Movie movie) {
        return new MovieResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getDescription(),
                movie.getGenre(),
                movie.getDurationMinutes(),
                movie.getAgeRating(),
                movie.getDirector(),
                movie.getReleaseDate(),
                movie.getPosterUrl(),
                movie.getTrailerUrl());
    }
}
