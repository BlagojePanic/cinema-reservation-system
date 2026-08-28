package com.cryptocinema.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cryptocinema.entity.Seat;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    boolean existsByHallId(Long hallId);

    boolean existsByHallIdAndRowLabelIgnoreCaseAndSeatNumber(Long hallId, String rowLabel, int seatNumber);

    List<Seat> findByHallIdOrderByRowLabelAscSeatNumberAsc(Long hallId);
}
