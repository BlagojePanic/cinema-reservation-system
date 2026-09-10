package com.cryptocinema.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cryptocinema.entity.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    boolean existsByReservationId(Long reservationId);

    boolean existsByTicketCode(String ticketCode);

    Optional<Ticket> findByReservationId(Long reservationId);

    Optional<Ticket> findByTicketCode(String ticketCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from Ticket ticket where ticket.ticketCode = :ticketCode")
    Optional<Ticket> findByTicketCodeForUpdate(@Param("ticketCode") String ticketCode);
}
