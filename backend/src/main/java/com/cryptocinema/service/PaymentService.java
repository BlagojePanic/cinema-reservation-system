package com.cryptocinema.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cryptocinema.dto.AdminPaymentResponse;
import com.cryptocinema.dto.PaymentRequest;
import com.cryptocinema.dto.PaymentResponse;
import com.cryptocinema.entity.Payment;
import com.cryptocinema.entity.PaymentMethod;
import com.cryptocinema.entity.PaymentStatus;
import com.cryptocinema.entity.Reservation;
import com.cryptocinema.entity.ReservationStatus;
import com.cryptocinema.entity.User;
import com.cryptocinema.exception.ConflictException;
import com.cryptocinema.exception.InvalidCredentialsException;
import com.cryptocinema.exception.ResourceNotFoundException;
import com.cryptocinema.repository.PaymentRepository;
import com.cryptocinema.repository.ReservationRepository;
import com.cryptocinema.repository.UserRepository;

@Service
public class PaymentService {

    private static final String CURRENCY_RSD = "RSD";

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final ReservationExpirationService reservationExpirationService;

    public PaymentService(
            PaymentRepository paymentRepository,
            ReservationRepository reservationRepository,
            UserRepository userRepository,
            ReservationExpirationService reservationExpirationService
    ) {
        this.paymentRepository = paymentRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.reservationExpirationService = reservationExpirationService;
    }

    @Transactional(noRollbackFor = ConflictException.class)
    public PaymentResponse create(Long reservationId, PaymentRequest request, Authentication authentication) {
        User currentUser = currentUser(authentication);
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
        ensureOwner(reservation, currentUser);

        if (reservationExpirationService.expireIfNeeded(reservation)) {
            throw new ConflictException("Reservation has expired and cannot be paid.");
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new ConflictException("Cancelled reservation cannot be paid.");
        }
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            throw new ConflictException("Reservation is already confirmed.");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            throw new ConflictException("Reservation is not pending payment.");
        }
        if (request.method() != PaymentMethod.CARD_SIMULATION) {
            throw new ConflictException("Only simulated card payment is available in this step.");
        }
        if (paymentRepository.existsByReservationIdAndStatus(reservation.getId(), PaymentStatus.SUCCESS)) {
            throw new ConflictException("Reservation already has a successful payment.");
        }

        LocalDateTime now = LocalDateTime.now();
        Payment payment = new Payment();
        payment.setReservation(reservation);
        payment.setUser(currentUser);
        payment.setAmount(reservation.getTotalAmount());
        payment.setCurrency(CURRENCY_RSD);
        payment.setMethod(request.method());
        payment.setStatus(request.simulateSuccess() ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        payment.setCreatedAt(now);
        payment.setCompletedAt(now);
        payment.setReference(generateReference());

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            reservation.setStatus(ReservationStatus.CONFIRMED);
            reservation.setUpdatedAt(now);
        }

        return toResponse(paymentRepository.save(payment));
    }

    @Transactional
    public List<PaymentResponse> findForReservation(Long reservationId, Authentication authentication) {
        User currentUser = currentUser(authentication);
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
        ensureOwner(reservation, currentUser);

        return paymentRepository.findByReservationIdOrderByCreatedAtDesc(reservationId).stream()
                .map(PaymentService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminPaymentResponse> findAllForAdmin() {
        return paymentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toAdminResponse)
                .toList();
    }

    public static PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getReservation().getId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getReference(),
                payment.getCreatedAt(),
                payment.getCompletedAt());
    }

    private AdminPaymentResponse toAdminResponse(Payment payment) {
        Reservation reservation = payment.getReservation();
        return new AdminPaymentResponse(
                payment.getId(),
                payment.getUser().getEmail(),
                reservation.getScreening().getMovie().getTitle(),
                reservation.getId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getReference(),
                payment.getCreatedAt(),
                payment.getCompletedAt());
    }

    private String generateReference() {
        String reference;
        do {
            reference = "PAY-" + UUID.randomUUID();
        } while (paymentRepository.existsByReference(reference));
        return reference;
    }

    private void ensureOwner(Reservation reservation, User currentUser) {
        if (!reservation.getUser().getId().equals(currentUser.getId())) {
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
}
