package com.cryptocinema.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cryptocinema.dto.HallRequest;
import com.cryptocinema.dto.HallResponse;
import com.cryptocinema.entity.Cinema;
import com.cryptocinema.entity.Hall;
import com.cryptocinema.exception.ConflictException;
import com.cryptocinema.exception.ResourceNotFoundException;
import com.cryptocinema.repository.CinemaRepository;
import com.cryptocinema.repository.HallRepository;
import com.cryptocinema.repository.ScreeningRepository;
import com.cryptocinema.repository.SeatRepository;

@Service
public class HallService {

    private final HallRepository hallRepository;
    private final CinemaRepository cinemaRepository;
    private final SeatRepository seatRepository;
    private final ScreeningRepository screeningRepository;

    public HallService(
            HallRepository hallRepository,
            CinemaRepository cinemaRepository,
            SeatRepository seatRepository,
            ScreeningRepository screeningRepository
    ) {
        this.hallRepository = hallRepository;
        this.cinemaRepository = cinemaRepository;
        this.seatRepository = seatRepository;
        this.screeningRepository = screeningRepository;
    }

    @Transactional(readOnly = true)
    public List<HallResponse> findByCinema(Long cinemaId) {
        if (!cinemaRepository.existsById(cinemaId)) {
            throw new ResourceNotFoundException("Cinema not found");
        }
        return hallRepository.findByCinemaIdOrderByNameAsc(cinemaId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public HallResponse findById(Long id) {
        return toResponse(getHall(id));
    }

    @Transactional
    public HallResponse create(HallRequest request) {
        Hall hall = new Hall();
        applyRequest(hall, request);
        return toResponse(hallRepository.save(hall));
    }

    @Transactional
    public HallResponse update(Long id, HallRequest request) {
        Hall hall = getHall(id);
        applyRequest(hall, request);
        return toResponse(hall);
    }

    @Transactional
    public void delete(Long id) {
        Hall hall = getHall(id);
        if (seatRepository.existsByHallId(id)) {
            throw new ConflictException("Hall cannot be deleted while it has seats");
        }
        if (screeningRepository.existsByHallId(id)) {
            throw new ConflictException("Hall cannot be deleted while it has screenings");
        }
        hallRepository.delete(hall);
    }

    private void applyRequest(Hall hall, HallRequest request) {
        Cinema cinema = cinemaRepository.findById(request.cinemaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cinema not found"));
        hall.setName(request.name().trim());
        hall.setCinema(cinema);
    }

    private Hall getHall(Long id) {
        return hallRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hall not found"));
    }

    private HallResponse toResponse(Hall hall) {
        Cinema cinema = hall.getCinema();
        return new HallResponse(hall.getId(), hall.getName(), cinema.getId(), cinema.getName());
    }
}
