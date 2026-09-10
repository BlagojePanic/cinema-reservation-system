package com.cryptocinema.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.cryptocinema.dto.MovieRequest;
import com.cryptocinema.dto.MovieResponse;
import com.cryptocinema.entity.Movie;
import com.cryptocinema.entity.MovieStatus;
import com.cryptocinema.exception.ConflictException;
import com.cryptocinema.exception.ResourceNotFoundException;
import com.cryptocinema.repository.MovieRepository;
import com.cryptocinema.repository.ScreeningRepository;

@Service
public class MovieService {

    private final MovieRepository movieRepository;
    private final ScreeningRepository screeningRepository;
    private final MediaStorageService mediaStorageService;

    public MovieService(
            MovieRepository movieRepository,
            ScreeningRepository screeningRepository,
            MediaStorageService mediaStorageService
    ) {
        this.movieRepository = movieRepository;
        this.screeningRepository = screeningRepository;
        this.mediaStorageService = mediaStorageService;
    }

    @Transactional(readOnly = true)
    public List<MovieResponse> findAll() {
        return movieRepository.findActiveMovies().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MovieResponse> findAllForAdmin() {
        return movieRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MovieResponse findById(Long id) {
        Movie movie = getMovie(id);
        if (isArchived(movie)) {
            throw new ResourceNotFoundException("Movie not found");
        }
        return toResponse(movie);
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
        if (screeningRepository.existsByMovieIdAndStartTimeGreaterThanEqual(id, LocalDateTime.now())) {
            throw new ConflictException("Movie cannot be archived while future screenings exist.");
        }
        if (!screeningRepository.existsByMovieId(id)) {
            movieRepository.delete(movie);
            return;
        }
        movie.setStatus(MovieStatus.ARCHIVED);
    }

    @Transactional
    public MovieResponse restore(Long id) {
        Movie movie = getMovie(id);
        movie.setStatus(MovieStatus.ACTIVE);
        return toResponse(movie);
    }

    @Transactional
    public MovieResponse uploadPoster(Long id, MultipartFile file) {
        Movie movie = getMovie(id);
        movie.setPosterUrl(mediaStorageService.storePoster(file));
        return toResponse(movie);
    }

    @Transactional
    public MovieResponse uploadTrailer(Long id, MultipartFile file) {
        Movie movie = getMovie(id);
        movie.setTrailerUrl(mediaStorageService.storeTrailer(file));
        return toResponse(movie);
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
        if (movie.getStatus() == null) {
            movie.setStatus(MovieStatus.ACTIVE);
        }
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
                movie.getTrailerUrl(),
                status(movie).name());
    }

    private boolean isArchived(Movie movie) {
        return status(movie) == MovieStatus.ARCHIVED;
    }

    private MovieStatus status(Movie movie) {
        return movie.getStatus() == null ? MovieStatus.ACTIVE : movie.getStatus();
    }
}
