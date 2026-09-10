package com.cryptocinema.service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cryptocinema.dto.AdminTicketResponse;
import com.cryptocinema.dto.TicketResponse;
import com.cryptocinema.dto.TicketValidationResponse;
import com.cryptocinema.entity.PaymentStatus;
import com.cryptocinema.entity.Reservation;
import com.cryptocinema.entity.ReservationStatus;
import com.cryptocinema.entity.Screening;
import com.cryptocinema.entity.Seat;
import com.cryptocinema.entity.Ticket;
import com.cryptocinema.entity.TicketStatus;
import com.cryptocinema.entity.User;
import com.cryptocinema.exception.ConflictException;
import com.cryptocinema.exception.InvalidCredentialsException;
import com.cryptocinema.exception.ResourceNotFoundException;
import com.cryptocinema.repository.PaymentRepository;
import com.cryptocinema.repository.ReservationRepository;
import com.cryptocinema.repository.ReservationSeatRepository;
import com.cryptocinema.repository.TicketRepository;
import com.cryptocinema.repository.UserRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

@Service
public class TicketService {

    private static final int QR_SIZE = 320;

    private final TicketRepository ticketRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    public TicketService(
            TicketRepository ticketRepository,
            ReservationRepository reservationRepository,
            ReservationSeatRepository reservationSeatRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.reservationRepository = reservationRepository;
        this.reservationSeatRepository = reservationSeatRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Ticket createTicketIfEligible(Reservation reservation) {
        if (reservation.getStatus() != ReservationStatus.CONFIRMED
                || !paymentRepository.existsByReservationIdAndStatus(reservation.getId(), PaymentStatus.SUCCESS)) {
            throw new ConflictException("Ticket can be created only for confirmed reservations with successful payment.");
        }

        return ticketRepository.findByReservationId(reservation.getId())
                .orElseGet(() -> saveNewTicket(reservation));
    }

    @Transactional(readOnly = true)
    public TicketResponse findForReservation(Long reservationId, Authentication authentication) {
        User currentUser = currentUser(authentication);
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
        ensureOwner(reservation, currentUser);
        validateTicketEligibility(reservation);
        Ticket ticket = ticketRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        return toUserResponse(ticket);
    }

    @Transactional(readOnly = true)
    public byte[] generateQrCode(Long reservationId, Authentication authentication) {
        TicketResponse ticket = findForReservation(reservationId, authentication);
        try {
            BitMatrix matrix = new QRCodeWriter().encode(ticket.ticketCode(), BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(MatrixToImageWriter.toBufferedImage(matrix), "PNG", output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new ConflictException("Could not generate QR code.");
        }
    }

    @Transactional(readOnly = true)
    public AdminTicketResponse inspect(String ticketCode) {
        Ticket ticket = ticketRepository.findByTicketCode(ticketCode)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        return toAdminResponse(ticket);
    }

    @Transactional
    public TicketValidationResponse validate(String ticketCode) {
        Ticket ticket = ticketRepository.findByTicketCodeForUpdate(ticketCode)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        if (ticket.getReservation().getStatus() != ReservationStatus.CONFIRMED) {
            throw new ConflictException("Reservation is not confirmed.");
        }
        if (ticket.getStatus() == TicketStatus.USED) {
            return new TicketValidationResponse(false, "Ticket is already used.", toAdminResponse(ticket));
        }

        ticket.setStatus(TicketStatus.USED);
        ticket.setUsedAt(LocalDateTime.now());
        return new TicketValidationResponse(true, "Ticket validated.", toAdminResponse(ticket));
    }

    private Ticket saveNewTicket(Reservation reservation) {
        Ticket ticket = new Ticket();
        ticket.setReservation(reservation);
        ticket.setTicketCode(generateTicketCode());
        ticket.setStatus(TicketStatus.VALID);
        ticket.setCreatedAt(LocalDateTime.now());
        try {
            return ticketRepository.save(ticket);
        } catch (DataIntegrityViolationException exception) {
            return ticketRepository.findByReservationId(reservation.getId())
                    .orElseThrow(() -> exception);
        }
    }

    private void validateTicketEligibility(Reservation reservation) {
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new ConflictException("Ticket is available only for confirmed reservations.");
        }
        if (!paymentRepository.existsByReservationIdAndStatus(reservation.getId(), PaymentStatus.SUCCESS)) {
            throw new ConflictException("Ticket is available only after successful payment.");
        }
    }

    private String generateTicketCode() {
        String code;
        do {
            code = "TCK-" + UUID.randomUUID();
        } while (ticketRepository.existsByTicketCode(code));
        return code;
    }

    private TicketResponse toUserResponse(Ticket ticket) {
        Reservation reservation = ticket.getReservation();
        Screening screening = reservation.getScreening();
        return new TicketResponse(
                ticket.getTicketCode(),
                ticket.getStatus(),
                screening.getMovie().getTitle(),
                screening.getHall().getCinema().getName(),
                screening.getHall().getName(),
                screening.getStartTime(),
                seatLabels(reservation.getId()),
                reservation.getTotalAmount(),
                reservation.getId(),
                ticket.getCreatedAt(),
                ticket.getUsedAt());
    }

    private AdminTicketResponse toAdminResponse(Ticket ticket) {
        Reservation reservation = ticket.getReservation();
        Screening screening = reservation.getScreening();
        return new AdminTicketResponse(
                ticket.getTicketCode(),
                ticket.getStatus(),
                reservation.getStatus(),
                screening.getMovie().getTitle(),
                screening.getStartTime(),
                screening.getHall().getCinema().getName(),
                screening.getHall().getName(),
                seatLabels(reservation.getId()),
                reservation.getUser().getEmail(),
                reservation.getId(),
                ticket.getCreatedAt(),
                ticket.getUsedAt());
    }

    private List<String> seatLabels(Long reservationId) {
        return reservationSeatRepository.findByReservationId(reservationId).stream()
                .map(reservationSeat -> label(reservationSeat.getScreeningSeat().getSeat()))
                .sorted()
                .toList();
    }

    private String label(Seat seat) {
        return seat.getRowLabel() + seat.getSeatNumber();
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
