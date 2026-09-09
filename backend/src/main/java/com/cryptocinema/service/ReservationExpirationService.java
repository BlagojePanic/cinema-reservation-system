package com.cryptocinema.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cryptocinema.entity.Reservation;
import com.cryptocinema.entity.ReservationStatus;
import com.cryptocinema.entity.ScreeningSeat;
import com.cryptocinema.entity.ScreeningSeatStatus;
import com.cryptocinema.repository.ReservationRepository;
import com.cryptocinema.repository.ReservationSeatRepository;

@Service
public class ReservationExpirationService {

    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;

    public ReservationExpirationService(
            ReservationRepository reservationRepository,
            ReservationSeatRepository reservationSeatRepository
    ) {
        this.reservationRepository = reservationRepository;
        this.reservationSeatRepository = reservationSeatRepository;
    }

    @Transactional
    public void expirePendingReservations() {
        LocalDateTime now = LocalDateTime.now();
        reservationRepository.findByStatus(ReservationStatus.PENDING_PAYMENT).stream()
                .filter(reservation -> reservation.getExpiresAt().isBefore(now))
                .forEach(this::expire);
    }

    private void expire(Reservation reservation) {
        reservation.setStatus(ReservationStatus.EXPIRED);
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationSeatRepository.findByReservationId(reservation.getId()).forEach(reservationSeat -> {
            ScreeningSeat screeningSeat = reservationSeat.getScreeningSeat();
            if (screeningSeat.getStatus() == ScreeningSeatStatus.RESERVED) {
                screeningSeat.setStatus(ScreeningSeatStatus.AVAILABLE);
                screeningSeat.setHeldByUser(null);
                screeningSeat.setHoldExpiresAt(null);
            }
        });
    }
}
