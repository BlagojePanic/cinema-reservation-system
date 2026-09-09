package com.cryptocinema.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cryptocinema.entity.Payment;
import com.cryptocinema.entity.PaymentMethod;
import com.cryptocinema.entity.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByReservationIdAndStatus(Long reservationId, PaymentStatus status);

    boolean existsByReference(String reference);

    boolean existsByTransactionHash(String transactionHash);

    boolean existsByTransactionHashAndIdNot(String transactionHash, Long id);

    Optional<Payment> findFirstByReservationIdAndStatusOrderByCreatedAtDesc(Long reservationId, PaymentStatus status);

    Optional<Payment> findFirstByReservationIdOrderByCreatedAtDesc(Long reservationId);

    Optional<Payment> findFirstByReservationIdAndMethodAndStatusOrderByCreatedAtDesc(
            Long reservationId,
            PaymentMethod method,
            PaymentStatus status);

    List<Payment> findByReservationIdOrderByCreatedAtDesc(Long reservationId);

    List<Payment> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);
}
