package com.cryptocinema.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cryptocinema.dto.ReservationRequest;
import com.cryptocinema.dto.ReservationResponse;
import com.cryptocinema.dto.AdminReservationResponse;
import com.cryptocinema.entity.Payment;
import com.cryptocinema.entity.PaymentStatus;
import com.cryptocinema.entity.Reservation;
import com.cryptocinema.entity.ReservationSeat;
import com.cryptocinema.entity.ReservationStatus;
import com.cryptocinema.entity.Role;
import com.cryptocinema.entity.Screening;
import com.cryptocinema.entity.ScreeningSeat;
import com.cryptocinema.entity.ScreeningSeatStatus;
import com.cryptocinema.entity.Seat;
import com.cryptocinema.entity.User;
import com.cryptocinema.exception.ConflictException;
import com.cryptocinema.exception.InvalidCredentialsException;
import com.cryptocinema.exception.ResourceNotFoundException;
import com.cryptocinema.repository.ReservationRepository;
import com.cryptocinema.repository.ReservationSeatRepository;
import com.cryptocinema.repository.ScreeningRepository;
import com.cryptocinema.repository.ScreeningSeatRepository;
import com.cryptocinema.repository.UserRepository;
import com.cryptocinema.repository.PaymentRepository;

@Service
public class ReservationService {

    private static final int PAYMENT_WINDOW_MINUTES = 10;

    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final PaymentRepository paymentRepository;
    private final ScreeningRepository screeningRepository;
    private final ScreeningSeatRepository screeningSeatRepository;
    private final UserRepository userRepository;
    private final ReservationExpirationService reservationExpirationService;

    public ReservationService(
            ReservationRepository reservationRepository,
            ReservationSeatRepository reservationSeatRepository,
            PaymentRepository paymentRepository,
            ScreeningRepository screeningRepository,
            ScreeningSeatRepository screeningSeatRepository,
            UserRepository userRepository,
            ReservationExpirationService reservationExpirationService
    ) {
        this.reservationRepository = reservationRepository;
        this.reservationSeatRepository = reservationSeatRepository;
        this.paymentRepository = paymentRepository;
        this.screeningRepository = screeningRepository;
        this.screeningSeatRepository = screeningSeatRepository;
        this.userRepository = userRepository;
        this.reservationExpirationService = reservationExpirationService;
    }

    @Transactional
    public ReservationResponse create(ReservationRequest request, Authentication authentication) {
        reservationExpirationService.expirePendingReservations();
        User currentUser = currentUser(authentication);
        Screening screening = screeningRepository.findById(request.screeningId())
                .orElseThrow(() -> new ResourceNotFoundException("Screening not found"));

        List<Long> requestedSeatIds = request.screeningSeatIds();
        if (new HashSet<>(requestedSeatIds).size() != requestedSeatIds.size()) {
            throw new ConflictException("Duplicate seats in reservation request.");
        }

        List<ScreeningSeat> screeningSeats = screeningSeatRepository.findAllByIdForUpdate(requestedSeatIds);
        if (screeningSeats.size() != requestedSeatIds.size()) {
            throw new ResourceNotFoundException("Screening seat not found");
        }

        LocalDateTime now = LocalDateTime.now();
        for (ScreeningSeat screeningSeat : screeningSeats) {
            validateReservableSeat(screening, screeningSeat, currentUser, now);
        }

        if (reservationSeatRepository.existsActiveByScreeningSeatIdIn(
                requestedSeatIds,
                List.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED))) {
            throw new ConflictException("Seat is already part of an active reservation.");
        }

        Reservation reservation = new Reservation();
        reservation.setUser(currentUser);
        reservation.setScreening(screening);
        reservation.setStatus(ReservationStatus.PENDING_PAYMENT);
        reservation.setCreatedAt(now);
        reservation.setUpdatedAt(now);
        reservation.setExpiresAt(now.plusMinutes(PAYMENT_WINDOW_MINUTES));
        reservation.setTotalAmount(screening.getTicketPrice().multiply(BigDecimal.valueOf(screeningSeats.size())));
        Reservation savedReservation = reservationRepository.save(reservation);

        List<ReservationSeat> reservationSeats = new ArrayList<>();
        for (ScreeningSeat screeningSeat : screeningSeats) {
            screeningSeat.setStatus(ScreeningSeatStatus.RESERVED);
            screeningSeat.setHeldByUser(null);
            screeningSeat.setHoldExpiresAt(null);

            ReservationSeat reservationSeat = new ReservationSeat();
            reservationSeat.setReservation(savedReservation);
            reservationSeat.setScreeningSeat(screeningSeat);
            reservationSeats.add(reservationSeat);
        }
        reservationSeatRepository.saveAll(reservationSeats);

        return toResponse(savedReservation);
    }

    @Transactional
    public List<ReservationResponse> findMine(Authentication authentication) {
        reservationExpirationService.expirePendingReservations();
        User currentUser = currentUser(authentication);
        return reservationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ReservationResponse findById(Long id, Authentication authentication) {
        reservationExpirationService.expirePendingReservations();
        User currentUser = currentUser(authentication);
        Reservation reservation = getReservation(id);
        ensureOwnerOrAdmin(reservation, currentUser);
        return toResponse(reservation);
    }

    @Transactional
    public List<AdminReservationResponse> findAllForAdmin() {
        reservationExpirationService.expirePendingReservations();
        return reservationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Transactional
    public ReservationResponse cancel(Long id, Authentication authentication) {
        reservationExpirationService.expirePendingReservations();
        User currentUser = currentUser(authentication);
        Reservation reservation = getReservation(id);
        ensureOwnerOrAdmin(reservation, currentUser);

        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            throw new ConflictException("Confirmed reservation cannot be cancelled here.");
        }
        if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT) {
            reservation.setStatus(ReservationStatus.CANCELLED);
            reservation.setUpdatedAt(LocalDateTime.now());
            releaseReservedSeats(reservation);
        }

        return toResponse(reservation);
    }

    private void validateReservableSeat(
            Screening screening,
            ScreeningSeat screeningSeat,
            User currentUser,
            LocalDateTime now
    ) {
        if (!screeningSeat.getScreening().getId().equals(screening.getId())) {
            throw new ConflictException("All seats must belong to the selected screening.");
        }
        if (screeningSeat.getStatus() == ScreeningSeatStatus.HELD
                && screeningSeat.getHoldExpiresAt() != null
                && screeningSeat.getHoldExpiresAt().isBefore(now)) {
            screeningSeat.setStatus(ScreeningSeatStatus.AVAILABLE);
            screeningSeat.setHeldByUser(null);
            screeningSeat.setHoldExpiresAt(null);
            throw new ConflictException("Seat hold has expired.");
        }
        if (screeningSeat.getStatus() != ScreeningSeatStatus.HELD) {
            throw new ConflictException("Seat must be held before reservation.");
        }
        if (screeningSeat.getHeldByUser() == null
                || !screeningSeat.getHeldByUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Forbidden");
        }
    }

    private void releaseReservedSeats(Reservation reservation) {
        reservationSeatRepository.findByReservationId(reservation.getId()).forEach(reservationSeat -> {
            ScreeningSeat screeningSeat = reservationSeat.getScreeningSeat();
            if (screeningSeat.getStatus() == ScreeningSeatStatus.RESERVED) {
                screeningSeat.setStatus(ScreeningSeatStatus.AVAILABLE);
                screeningSeat.setHeldByUser(null);
                screeningSeat.setHoldExpiresAt(null);
            }
        });
    }

    private Reservation getReservation(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
    }

    private void ensureOwnerOrAdmin(Reservation reservation, User currentUser) {
        boolean owner = reservation.getUser().getId().equals(currentUser.getId());
        if (!owner && currentUser.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Forbidden");
        }
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InvalidCredentialsException("Invalid authentication");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid authentication"));
    }

    private ReservationResponse toResponse(Reservation reservation) {
        Screening screening = reservation.getScreening();
        List<String> seatLabels = reservationSeatRepository.findByReservationId(reservation.getId()).stream()
                .map(reservationSeat -> label(reservationSeat.getScreeningSeat().getSeat()))
                .sorted()
                .toList();

        return new ReservationResponse(
                reservation.getId(),
                reservation.getStatus(),
                screening.getMovie().getTitle(),
                screening.getId(),
                screening.getStartTime(),
                screening.getHall().getCinema().getName(),
                screening.getHall().getName(),
                seatLabels,
                screening.getTicketPrice(),
                reservation.getTotalAmount(),
                reservation.getCreatedAt(),
                reservation.getExpiresAt(),
                paymentRepository.findFirstByReservationIdAndStatusOrderByCreatedAtDesc(
                                reservation.getId(),
                                PaymentStatus.SUCCESS)
                        .map(PaymentService::toResponse)
                        .orElse(null));
    }

    private AdminReservationResponse toAdminResponse(Reservation reservation) {
        Screening screening = reservation.getScreening();
        List<String> seatLabels = reservationSeatRepository.findByReservationId(reservation.getId()).stream()
                .map(reservationSeat -> label(reservationSeat.getScreeningSeat().getSeat()))
                .sorted()
                .toList();
        Payment latestPayment = paymentRepository.findFirstByReservationIdOrderByCreatedAtDesc(reservation.getId())
                .orElse(null);

        return new AdminReservationResponse(
                reservation.getId(),
                reservation.getUser().getId(),
                reservation.getUser().getEmail(),
                screening.getMovie().getTitle(),
                screening.getId(),
                screening.getStartTime(),
                screening.getHall().getCinema().getName(),
                screening.getHall().getName(),
                seatLabels,
                reservation.getTotalAmount(),
                reservation.getStatus(),
                reservation.getCreatedAt(),
                reservation.getExpiresAt(),
                latestPayment == null ? null : latestPayment.getStatus(),
                latestPayment == null ? null : latestPayment.getMethod(),
                latestPayment == null ? null : latestPayment.getReference(),
                latestPayment == null ? null : latestPayment.getTransactionHash());
    }

    private String label(Seat seat) {
        return seat.getRowLabel() + seat.getSeatNumber();
    }
}
