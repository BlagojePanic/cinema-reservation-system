package com.cryptocinema.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cryptocinema.entity.Cinema;

public interface CinemaRepository extends JpaRepository<Cinema, Long> {

    boolean existsByCityId(Long cityId);

    List<Cinema> findByCityIdOrderByNameAsc(Long cityId);
}
