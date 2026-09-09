package com.cryptocinema.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cryptocinema.entity.Reservation;
import com.cryptocinema.entity.ReservationStatus;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    boolean existsByScreeningIdAndStatusIn(Long screeningId, Collection<ReservationStatus> statuses);

    List<Reservation> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Reservation> findByStatus(ReservationStatus status);
}
