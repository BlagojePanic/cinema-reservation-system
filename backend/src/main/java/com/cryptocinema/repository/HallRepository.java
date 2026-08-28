package com.cryptocinema.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cryptocinema.entity.Hall;

public interface HallRepository extends JpaRepository<Hall, Long> {

    boolean existsByCinemaId(Long cinemaId);

    List<Hall> findByCinemaIdOrderByNameAsc(Long cinemaId);
}
