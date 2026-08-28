package com.cryptocinema.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cryptocinema.entity.City;

public interface CityRepository extends JpaRepository<City, Long> {
}
