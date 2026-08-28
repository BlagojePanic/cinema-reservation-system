package com.cryptocinema.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cryptocinema.entity.Screening;

public interface ScreeningRepository extends JpaRepository<Screening, Long> {

    boolean existsByMovieId(Long movieId);

    boolean existsByHallId(Long hallId);

    List<Screening> findByMovieId(Long movieId);

    List<Screening> findByHallId(Long hallId);
}
