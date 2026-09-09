package com.cryptocinema.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cryptocinema.entity.Payment;
import com.cryptocinema.entity.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByReservationIdAndStatus(Long reservationId, PaymentStatus status);

    boolean existsByReference(String reference);

    Optional<Payment> findFirstByReservationIdAndStatusOrderByCreatedAtDesc(Long reservationId, PaymentStatus status);

    List<Payment> findByReservationIdOrderByCreatedAtDesc(Long reservationId);

    List<Payment> findAllByOrderByCreatedAtDesc();
}
