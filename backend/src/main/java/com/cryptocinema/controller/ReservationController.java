package com.cryptocinema.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cryptocinema.dto.PaymentRequest;
import com.cryptocinema.dto.PaymentResponse;
import com.cryptocinema.dto.CryptoPaymentConfirmRequest;
import com.cryptocinema.dto.CryptoPaymentPrepareResponse;
import com.cryptocinema.dto.ReservationRequest;
import com.cryptocinema.dto.ReservationResponse;
import com.cryptocinema.dto.TicketResponse;
import com.cryptocinema.service.PaymentService;
import com.cryptocinema.service.ReservationService;
import com.cryptocinema.service.TicketService;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final PaymentService paymentService;
    private final TicketService ticketService;

    public ReservationController(
            ReservationService reservationService,
            PaymentService paymentService,
            TicketService ticketService
    ) {
        this.reservationService = reservationService;
        this.paymentService = paymentService;
        this.ticketService = ticketService;
    }

    @PostMapping
    public ReservationResponse create(
            @Valid @RequestBody ReservationRequest request,
            Authentication authentication
    ) {
        return reservationService.create(request, authentication);
    }

    @GetMapping("/me")
    public List<ReservationResponse> findMine(Authentication authentication) {
        return reservationService.findMine(authentication);
    }

    @GetMapping("/{id}")
    public ReservationResponse findById(@PathVariable Long id, Authentication authentication) {
        return reservationService.findById(id, authentication);
    }

    @PostMapping("/{id}/cancel")
    public ReservationResponse cancel(@PathVariable Long id, Authentication authentication) {
        return reservationService.cancel(id, authentication);
    }

    @PostMapping("/{id}/payment")
    public PaymentResponse createPayment(
            @PathVariable Long id,
            @Valid @RequestBody PaymentRequest request,
            Authentication authentication
    ) {
        return paymentService.create(id, request, authentication);
    }

    @GetMapping("/{id}/payments")
    public List<PaymentResponse> findPayments(@PathVariable Long id, Authentication authentication) {
        return paymentService.findForReservation(id, authentication);
    }

    @GetMapping("/{id}/ticket")
    public TicketResponse findTicket(@PathVariable Long id, Authentication authentication) {
        return ticketService.findForReservation(id, authentication);
    }

    @GetMapping(value = "/{id}/ticket/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] findTicketQr(@PathVariable Long id, Authentication authentication) {
        return ticketService.generateQrCode(id, authentication);
    }

    @PostMapping("/{id}/crypto-payment/prepare")
    public CryptoPaymentPrepareResponse prepareCryptoPayment(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return paymentService.prepareCryptoPayment(id, authentication);
    }

    @PostMapping("/{id}/crypto-payment/confirm")
    public PaymentResponse confirmCryptoPayment(
            @PathVariable Long id,
            @Valid @RequestBody CryptoPaymentConfirmRequest request,
            Authentication authentication
    ) {
        return paymentService.confirmCryptoPayment(id, request, authentication);
    }
}
