package com.cryptocinema.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cryptocinema.dto.SeatGenerationRequest;
import com.cryptocinema.dto.SeatRequest;
import com.cryptocinema.dto.SeatResponse;
import com.cryptocinema.entity.Hall;
import com.cryptocinema.entity.Seat;
import com.cryptocinema.exception.ConflictException;
import com.cryptocinema.exception.ResourceNotFoundException;
import com.cryptocinema.repository.HallRepository;
import com.cryptocinema.repository.ScreeningSeatRepository;
import com.cryptocinema.repository.SeatRepository;

@Service
public class SeatService {

    private final SeatRepository seatRepository;
    private final HallRepository hallRepository;
    private final ScreeningSeatRepository screeningSeatRepository;

    public SeatService(
            SeatRepository seatRepository,
            HallRepository hallRepository,
            ScreeningSeatRepository screeningSeatRepository
    ) {
        this.seatRepository = seatRepository;
        this.hallRepository = hallRepository;
        this.screeningSeatRepository = screeningSeatRepository;
    }

    @Transactional(readOnly = true)
    public List<SeatResponse> findByHall(Long hallId) {
        if (!hallRepository.existsById(hallId)) {
            throw new ResourceNotFoundException("Hall not found");
        }
        return seatRepository.findByHallIdOrderByRowLabelAscSeatNumberAsc(hallId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SeatResponse create(SeatRequest request) {
        Hall hall = getHall(request.hallId());
        String rowLabel = normalizeRowLabel(request.rowLabel());
        ensureSeatDoesNotExist(hall.getId(), rowLabel, request.seatNumber());

        Seat seat = new Seat();
        seat.setHall(hall);
        seat.setRowLabel(rowLabel);
        seat.setSeatNumber(request.seatNumber());
        return toResponse(seatRepository.save(seat));
    }

    @Transactional
    public List<SeatResponse> generate(Long hallId, SeatGenerationRequest request) {
        Hall hall = getHall(hallId);
        List<Seat> seats = new ArrayList<>();

        for (int rowIndex = 0; rowIndex < request.rows(); rowIndex++) {
            String rowLabel = rowLabelFor(rowIndex);
            for (int seatNumber = 1; seatNumber <= request.seatsPerRow(); seatNumber++) {
                ensureSeatDoesNotExist(hallId, rowLabel, seatNumber);

                Seat seat = new Seat();
                seat.setHall(hall);
                seat.setRowLabel(rowLabel);
                seat.setSeatNumber(seatNumber);
                seats.add(seat);
            }
        }

        return seatRepository.saveAll(seats).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void delete(Long id) {
        Seat seat = seatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found"));
        if (screeningSeatRepository.existsBySeatId(id)) {
            throw new ConflictException("Seat cannot be deleted while it is used by screenings");
        }
        seatRepository.delete(seat);
    }

    private void ensureSeatDoesNotExist(Long hallId, String rowLabel, int seatNumber) {
        if (seatRepository.existsByHallIdAndRowLabelIgnoreCaseAndSeatNumber(hallId, rowLabel, seatNumber)) {
            throw new ConflictException("Seat already exists in this hall");
        }
    }

    private Hall getHall(Long hallId) {
        return hallRepository.findById(hallId)
                .orElseThrow(() -> new ResourceNotFoundException("Hall not found"));
    }

    private String normalizeRowLabel(String rowLabel) {
        return rowLabel.trim().toUpperCase();
    }

    private String rowLabelFor(int rowIndex) {
        StringBuilder label = new StringBuilder();
        int value = rowIndex;
        do {
            label.insert(0, (char) ('A' + (value % 26)));
            value = value / 26 - 1;
        } while (value >= 0);
        return label.toString();
    }

    private SeatResponse toResponse(Seat seat) {
        return new SeatResponse(
                seat.getId(),
                seat.getRowLabel(),
                seat.getSeatNumber(),
                seat.getHall().getId());
    }
}
