package com.cryptocinema.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cryptocinema.dto.CinemaRequest;
import com.cryptocinema.dto.CinemaResponse;
import com.cryptocinema.dto.CityResponse;
import com.cryptocinema.entity.Cinema;
import com.cryptocinema.entity.City;
import com.cryptocinema.exception.ConflictException;
import com.cryptocinema.exception.ResourceNotFoundException;
import com.cryptocinema.repository.CinemaRepository;
import com.cryptocinema.repository.CityRepository;
import com.cryptocinema.repository.HallRepository;

@Service
public class CinemaService {

    private final CinemaRepository cinemaRepository;
    private final CityRepository cityRepository;
    private final HallRepository hallRepository;

    public CinemaService(
            CinemaRepository cinemaRepository,
            CityRepository cityRepository,
            HallRepository hallRepository
    ) {
        this.cinemaRepository = cinemaRepository;
        this.cityRepository = cityRepository;
        this.hallRepository = hallRepository;
    }

    @Transactional(readOnly = true)
    public List<CinemaResponse> findAll() {
        return cinemaRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CinemaResponse> findByCity(Long cityId) {
        if (!cityRepository.existsById(cityId)) {
            throw new ResourceNotFoundException("City not found");
        }
        return cinemaRepository.findByCityIdOrderByNameAsc(cityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CinemaResponse findById(Long id) {
        return toResponse(getCinema(id));
    }

    @Transactional
    public CinemaResponse create(CinemaRequest request) {
        Cinema cinema = new Cinema();
        applyRequest(cinema, request);
        return toResponse(cinemaRepository.save(cinema));
    }

    @Transactional
    public CinemaResponse update(Long id, CinemaRequest request) {
        Cinema cinema = getCinema(id);
        applyRequest(cinema, request);
        return toResponse(cinema);
    }

    @Transactional
    public void delete(Long id) {
        Cinema cinema = getCinema(id);
        if (hallRepository.existsByCinemaId(id)) {
            throw new ConflictException("Cinema cannot be deleted while it has halls");
        }
        cinemaRepository.delete(cinema);
    }

    private void applyRequest(Cinema cinema, CinemaRequest request) {
        City city = cityRepository.findById(request.cityId())
                .orElseThrow(() -> new ResourceNotFoundException("City not found"));
        cinema.setName(request.name().trim());
        cinema.setAddress(request.address().trim());
        cinema.setCity(city);
    }

    private Cinema getCinema(Long id) {
        return cinemaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cinema not found"));
    }

    private CinemaResponse toResponse(Cinema cinema) {
        City city = cinema.getCity();
        return new CinemaResponse(
                cinema.getId(),
                cinema.getName(),
                cinema.getAddress(),
                new CityResponse(city.getId(), city.getName()));
    }
}
