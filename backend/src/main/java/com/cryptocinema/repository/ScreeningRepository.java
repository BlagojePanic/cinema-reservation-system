package com.cryptocinema.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cryptocinema.entity.Screening;

public interface ScreeningRepository extends JpaRepository<Screening, Long> {

    boolean existsByMovieId(Long movieId);

    boolean existsByMovieIdAndStartTimeGreaterThanEqual(Long movieId, LocalDateTime startTime);

    boolean existsByHallId(Long hallId);

    List<Screening> findByMovieId(Long movieId);

    List<Screening> findByHallId(Long hallId);
}
