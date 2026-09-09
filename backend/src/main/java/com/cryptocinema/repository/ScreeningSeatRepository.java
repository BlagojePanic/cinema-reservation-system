package com.cryptocinema.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.cryptocinema.entity.ScreeningSeat;
import com.cryptocinema.entity.ScreeningSeatStatus;

public interface ScreeningSeatRepository extends JpaRepository<ScreeningSeat, Long> {

    boolean existsBySeatId(Long seatId);

    boolean existsByScreeningIdAndStatusIn(Long screeningId, Collection<ScreeningSeatStatus> statuses);

    long countByScreeningId(Long screeningId);

    List<ScreeningSeat> findByScreeningId(Long screeningId);

    List<ScreeningSeat> findByScreeningIdOrderBySeatRowLabelAscSeatSeatNumberAsc(Long screeningId);

    Optional<ScreeningSeat> findByScreeningIdAndSeatId(Long screeningId, Long seatId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select screeningSeat from ScreeningSeat screeningSeat "
            + "where screeningSeat.id = :id and screeningSeat.screening.id = :screeningId")
    Optional<ScreeningSeat> findByIdAndScreeningIdForUpdate(@Param("id") Long id, @Param("screeningId") Long screeningId);

    @Modifying
    @Transactional
    void deleteByScreeningId(Long screeningId);
}
