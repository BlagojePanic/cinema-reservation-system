package com.cryptocinema.repository;

import java.util.List;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cryptocinema.entity.ReservationSeat;
import com.cryptocinema.entity.ReservationStatus;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {

    @Query("""
            select count(reservationSeat) > 0
            from ReservationSeat reservationSeat
            where reservationSeat.screeningSeat.id in :screeningSeatIds
              and reservationSeat.reservation.status in :statuses
            """)
    boolean existsActiveByScreeningSeatIdIn(
            @Param("screeningSeatIds") List<Long> screeningSeatIds,
            @Param("statuses") Collection<ReservationStatus> statuses);

    boolean existsByScreeningSeatSeatId(Long seatId);

    List<ReservationSeat> findByReservationId(Long reservationId);
}
