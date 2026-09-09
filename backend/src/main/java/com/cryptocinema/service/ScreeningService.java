package com.cryptocinema.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cryptocinema.dto.ScreeningRequest;
import com.cryptocinema.dto.ScreeningResponse;
import com.cryptocinema.entity.Cinema;
import com.cryptocinema.entity.City;
import com.cryptocinema.entity.Hall;
import com.cryptocinema.entity.Movie;
import com.cryptocinema.entity.Seat;
import com.cryptocinema.entity.Screening;
import com.cryptocinema.entity.ScreeningSeat;
import com.cryptocinema.entity.ScreeningSeatStatus;
import com.cryptocinema.exception.ConflictException;
import com.cryptocinema.exception.ResourceNotFoundException;
import com.cryptocinema.repository.HallRepository;
import com.cryptocinema.repository.MovieRepository;
import com.cryptocinema.repository.ScreeningSeatRepository;
import com.cryptocinema.repository.ScreeningRepository;
import com.cryptocinema.repository.SeatRepository;

@Service
public class ScreeningService {

    private static final int CLEANUP_BUFFER_MINUTES = 15;

    private final ScreeningRepository screeningRepository;
    private final ScreeningSeatRepository screeningSeatRepository;
    private final MovieRepository movieRepository;
    private final HallRepository hallRepository;
    private final SeatRepository seatRepository;

    public ScreeningService(
            ScreeningRepository screeningRepository,
            ScreeningSeatRepository screeningSeatRepository,
            MovieRepository movieRepository,
            HallRepository hallRepository,
            SeatRepository seatRepository
    ) {
        this.screeningRepository = screeningRepository;
        this.screeningSeatRepository = screeningSeatRepository;
        this.movieRepository = movieRepository;
        this.hallRepository = hallRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional(readOnly = true)
    public ScreeningResponse findById(Long id) {
        return toResponse(getScreening(id));
    }

    @Transactional(readOnly = true)
    public List<ScreeningResponse> findByMovie(Long movieId) {
        if (!movieRepository.existsById(movieId)) {
            throw new ResourceNotFoundException("Movie not found");
        }
        return screeningRepository.findByMovieId(movieId).stream()
                .filter(this::isFutureOrCurrent)
                .sorted(Comparator.comparing(Screening::getStartTime))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScreeningResponse> findByHall(Long hallId) {
        if (!hallRepository.existsById(hallId)) {
            throw new ResourceNotFoundException("Hall not found");
        }
        return screeningRepository.findByHallId(hallId).stream()
                .filter(this::isFutureOrCurrent)
                .sorted(Comparator.comparing(Screening::getStartTime))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScreeningResponse> findFiltered(Long cityId, Long cinemaId, Long movieId, LocalDate date) {
        return screeningRepository.findAll().stream()
                .filter(this::isFutureOrCurrent)
                .filter(screening -> cityId == null || screening.getHall().getCinema().getCity().getId().equals(cityId))
                .filter(screening -> cinemaId == null || screening.getHall().getCinema().getId().equals(cinemaId))
                .filter(screening -> movieId == null || screening.getMovie().getId().equals(movieId))
                .filter(screening -> date == null || screening.getStartTime().toLocalDate().equals(date))
                .sorted(Comparator.comparing(Screening::getStartTime))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ScreeningResponse create(ScreeningRequest request) {
        Screening screening = new Screening();
        Movie movie = getMovie(request.movieId());
        Hall hall = getHall(request.hallId());
        ensureHallHasSeats(hall);
        ensureNoConflict(null, movie, hall, request.startTime());

        screening.setMovie(movie);
        screening.setHall(hall);
        screening.setStartTime(request.startTime());
        screening.setTicketPrice(request.ticketPrice());

        Screening savedScreening = screeningRepository.save(screening);
        initializeSeats(savedScreening);
        return toResponse(savedScreening);
    }

    @Transactional
    public ScreeningResponse update(Long id, ScreeningRequest request) {
        Screening screening = getScreening(id);
        Movie movie = getMovie(request.movieId());
        Hall hall = getHall(request.hallId());
        boolean hallChanged = !screening.getHall().getId().equals(hall.getId());

        if (hallChanged) {
            ensureNoHeldOrReservedSeats(screening.getId(), "Screening hall cannot be changed while it has held or reserved seats.");
            ensureHallHasSeats(hall);
        }

        ensureNoConflict(screening.getId(), movie, hall, request.startTime());

        if (hallChanged) {
            screeningSeatRepository.deleteByScreeningId(screening.getId());
        }

        screening.setMovie(movie);
        screening.setHall(hall);
        screening.setStartTime(request.startTime());
        screening.setTicketPrice(request.ticketPrice());

        if (hallChanged) {
            initializeSeats(screening);
        }

        return toResponse(screening);
    }

    @Transactional
    public void delete(Long id) {
        Screening screening = getScreening(id);
        ensureNoHeldOrReservedSeats(id, "Screening cannot be deleted while it has held or reserved seats.");
        screeningSeatRepository.deleteByScreeningId(id);
        screeningRepository.delete(screening);
    }

    private void ensureNoConflict(Long screeningId, Movie movie, Hall hall, LocalDateTime startTime) {
        LocalDateTime endTime = endTime(startTime, movie);

        boolean hasConflict = screeningRepository.findByHallId(hall.getId()).stream()
                .filter(existing -> screeningId == null || !existing.getId().equals(screeningId))
                .anyMatch(existing -> intervalsOverlap(
                        startTime,
                        endTime,
                        existing.getStartTime(),
                        endTime(existing.getStartTime(), existing.getMovie())));

        if (hasConflict) {
            throw new ConflictException("Screening conflicts with another screening in this hall.");
        }
    }

    void initializeMissingSeats(Screening screening) {
        initializeSeats(screening);
    }

    private void initializeSeats(Screening screening) {
        List<Seat> hallSeats = seatRepository.findByHallIdOrderByRowLabelAscSeatNumberAsc(screening.getHall().getId());
        if (hallSeats.isEmpty()) {
            throw new ConflictException("Screening cannot be created for a hall without seats.");
        }

        List<ScreeningSeat> screeningSeats = new ArrayList<>();
        for (Seat seat : hallSeats) {
            if (screeningSeatRepository.findByScreeningIdAndSeatId(screening.getId(), seat.getId()).isPresent()) {
                continue;
            }
            ScreeningSeat screeningSeat = new ScreeningSeat();
            screeningSeat.setScreening(screening);
            screeningSeat.setSeat(seat);
            screeningSeat.setStatus(ScreeningSeatStatus.AVAILABLE);
            screeningSeats.add(screeningSeat);
        }
        screeningSeatRepository.saveAll(screeningSeats);
    }

    private void ensureHallHasSeats(Hall hall) {
        if (!seatRepository.existsByHallId(hall.getId())) {
            throw new ConflictException("Screening cannot be created for a hall without seats.");
        }
    }

    private void ensureNoHeldOrReservedSeats(Long screeningId, String message) {
        Collection<ScreeningSeatStatus> blockingStatuses = List.of(
                ScreeningSeatStatus.HELD,
                ScreeningSeatStatus.RESERVED);
        if (screeningSeatRepository.existsByScreeningIdAndStatusIn(screeningId, blockingStatuses)) {
            throw new ConflictException(message);
        }
    }

    private Movie getMovie(Long movieId) {
        return movieRepository.findById(movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found"));
    }

    private Hall getHall(Long hallId) {
        return hallRepository.findById(hallId)
                .orElseThrow(() -> new ResourceNotFoundException("Hall not found"));
    }

    private LocalDateTime endTime(LocalDateTime startTime, Movie movie) {
        return startTime.plusMinutes(movie.getDurationMinutes() + CLEANUP_BUFFER_MINUTES);
    }

    private boolean intervalsOverlap(
            LocalDateTime firstStart,
            LocalDateTime firstEnd,
            LocalDateTime secondStart,
            LocalDateTime secondEnd
    ) {
        return firstStart.isBefore(secondEnd) && secondStart.isBefore(firstEnd);
    }

    private boolean isFutureOrCurrent(Screening screening) {
        return !screening.getStartTime().isBefore(LocalDateTime.now());
    }

    private Screening getScreening(Long id) {
        return screeningRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Screening not found"));
    }

    private ScreeningResponse toResponse(Screening screening) {
        Movie movie = screening.getMovie();
        Hall hall = screening.getHall();
        Cinema cinema = hall.getCinema();
        City city = cinema.getCity();
        return new ScreeningResponse(
                screening.getId(),
                movie.getId(),
                movie.getTitle(),
                hall.getId(),
                hall.getName(),
                cinema.getId(),
                cinema.getName(),
                city.getId(),
                city.getName(),
                screening.getStartTime(),
                screening.getTicketPrice());
    }
}
