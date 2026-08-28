package com.cryptocinema.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cryptocinema.dto.CityRequest;
import com.cryptocinema.dto.CityResponse;
import com.cryptocinema.entity.City;
import com.cryptocinema.exception.ConflictException;
import com.cryptocinema.exception.ResourceNotFoundException;
import com.cryptocinema.repository.CinemaRepository;
import com.cryptocinema.repository.CityRepository;

@Service
public class CityService {

    private final CityRepository cityRepository;
    private final CinemaRepository cinemaRepository;

    public CityService(CityRepository cityRepository, CinemaRepository cinemaRepository) {
        this.cityRepository = cityRepository;
        this.cinemaRepository = cinemaRepository;
    }

    @Transactional(readOnly = true)
    public List<CityResponse> findAll() {
        return cityRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CityResponse findById(Long id) {
        return toResponse(getCity(id));
    }

    @Transactional
    public CityResponse create(CityRequest request) {
        City city = new City();
        city.setName(request.name().trim());
        return toResponse(cityRepository.save(city));
    }

    @Transactional
    public CityResponse update(Long id, CityRequest request) {
        City city = getCity(id);
        city.setName(request.name().trim());
        return toResponse(city);
    }

    @Transactional
    public void delete(Long id) {
        City city = getCity(id);
        if (cinemaRepository.existsByCityId(id)) {
            throw new ConflictException("City cannot be deleted while it has cinemas");
        }
        cityRepository.delete(city);
    }

    private City getCity(Long id) {
        return cityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("City not found"));
    }

    private CityResponse toResponse(City city) {
        return new CityResponse(city.getId(), city.getName());
    }
}
