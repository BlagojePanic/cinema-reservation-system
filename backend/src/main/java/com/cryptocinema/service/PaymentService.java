package com.cryptocinema.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cryptocinema.dto.AdminPaymentResponse;
import com.cryptocinema.dto.CryptoPaymentConfirmRequest;
import com.cryptocinema.dto.CryptoPaymentPrepareResponse;
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
    private static final String CRYPTO_CURRENCY_ETH = "ETH";

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final ReservationExpirationService reservationExpirationService;
    private final CryptoTransactionVerifier cryptoTransactionVerifier;
    private final String merchantAddress;
    private final BigDecimal ethRsdRate;
    private final String cryptoNetwork;
    private final Long cryptoChainId;

    public PaymentService(
            PaymentRepository paymentRepository,
            ReservationRepository reservationRepository,
            UserRepository userRepository,
            ReservationExpirationService reservationExpirationService,
            CryptoTransactionVerifier cryptoTransactionVerifier,
            @Value("${app.crypto.merchant-address:}") String merchantAddress,
            @Value("${app.crypto.eth-rsd-rate:350000}") BigDecimal ethRsdRate,
            @Value("${app.crypto.network:Sepolia}") String cryptoNetwork,
            @Value("${app.crypto.chain-id:11155111}") Long cryptoChainId
    ) {
        this.paymentRepository = paymentRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.reservationExpirationService = reservationExpirationService;
        this.cryptoTransactionVerifier = cryptoTransactionVerifier;
        this.merchantAddress = merchantAddress;
        this.ethRsdRate = ethRsdRate;
        this.cryptoNetwork = cryptoNetwork;
        this.cryptoChainId = cryptoChainId;
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

    @Transactional(noRollbackFor = ConflictException.class)
    public CryptoPaymentPrepareResponse prepareCryptoPayment(Long reservationId, Authentication authentication) {
        User currentUser = currentUser(authentication);
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
        ensureOwner(reservation, currentUser);
        validatePayableReservation(reservation);
        validateCryptoConfiguration();

        Payment payment = paymentRepository.findFirstByReservationIdAndMethodAndStatusOrderByCreatedAtDesc(
                        reservation.getId(),
                        PaymentMethod.CRYPTO,
                        PaymentStatus.PENDING)
                .orElseGet(() -> createPendingCryptoPayment(reservation, currentUser));

        return toPrepareResponse(payment);
    }

    @Transactional(noRollbackFor = ConflictException.class)
    public PaymentResponse confirmCryptoPayment(
            Long reservationId,
            CryptoPaymentConfirmRequest request,
            Authentication authentication
    ) {
        User currentUser = currentUser(authentication);
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
        ensureOwner(reservation, currentUser);

        Payment payment = paymentRepository.findByIdForUpdate(request.paymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (!payment.getReservation().getId().equals(reservation.getId())) {
            throw new ConflictException("Payment does not belong to reservation.");
        }
        if (!payment.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Forbidden");
        }
        if (payment.getMethod() != PaymentMethod.CRYPTO) {
            throw new ConflictException("Payment is not a crypto payment.");
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ConflictException("Crypto payment is not pending.");
        }
        if (paymentRepository.existsByTransactionHashAndIdNot(request.transactionHash(), payment.getId())) {
            throw new ConflictException("Transaction hash is already used.");
        }
        if (reservationExpirationService.expireIfNeeded(reservation)) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setCompletedAt(LocalDateTime.now());
            throw new ConflictException("Reservation has expired and cannot be paid.");
        }
        validatePayableReservation(reservation);

        payment.setWalletAddress(request.walletAddress());
        payment.setTransactionHash(request.transactionHash());

        CryptoTransactionVerification verification = cryptoTransactionVerifier.verify(
                request.transactionHash(),
                merchantAddress,
                payment.getCryptoAmount(),
                cryptoChainId);

        if (verification.status() == CryptoTransactionVerificationStatus.PENDING) {
            return toResponse(payment);
        }
        if (verification.status() == CryptoTransactionVerificationStatus.FAILED) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setCompletedAt(LocalDateTime.now());
            throw new ConflictException(verification.message());
        }

        LocalDateTime now = LocalDateTime.now();
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setCompletedAt(now);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setUpdatedAt(now);
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
                payment.getCompletedAt(),
                payment.getCryptoCurrency(),
                payment.getCryptoAmount(),
                payment.getExchangeRate(),
                payment.getNetwork(),
                payment.getChainId(),
                payment.getWalletAddress(),
                payment.getTransactionHash());
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
                payment.getCompletedAt(),
                payment.getCryptoCurrency(),
                payment.getCryptoAmount(),
                payment.getExchangeRate(),
                payment.getNetwork(),
                payment.getChainId(),
                payment.getWalletAddress(),
                payment.getTransactionHash());
    }

    private void validatePayableReservation(Reservation reservation) {
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
        if (paymentRepository.existsByReservationIdAndStatus(reservation.getId(), PaymentStatus.SUCCESS)) {
            throw new ConflictException("Reservation already has a successful payment.");
        }
    }

    private Payment createPendingCryptoPayment(Reservation reservation, User currentUser) {
        LocalDateTime now = LocalDateTime.now();
        Payment payment = new Payment();
        payment.setReservation(reservation);
        payment.setUser(currentUser);
        payment.setAmount(reservation.getTotalAmount());
        payment.setCurrency(CURRENCY_RSD);
        payment.setMethod(PaymentMethod.CRYPTO);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCreatedAt(now);
        payment.setReference(generateReference());
        payment.setCryptoCurrency(CRYPTO_CURRENCY_ETH);
        payment.setCryptoAmount(calculateCryptoAmount(reservation.getTotalAmount()));
        payment.setExchangeRate(ethRsdRate);
        payment.setNetwork(cryptoNetwork);
        payment.setChainId(cryptoChainId);
        return paymentRepository.save(payment);
    }

    private CryptoPaymentPrepareResponse toPrepareResponse(Payment payment) {
        return new CryptoPaymentPrepareResponse(
                payment.getReservation().getId(),
                payment.getId(),
                merchantAddress,
                payment.getNetwork(),
                payment.getChainId(),
                payment.getCryptoCurrency(),
                payment.getCryptoAmount(),
                payment.getAmount(),
                payment.getReservation().getExpiresAt());
    }

    private BigDecimal calculateCryptoAmount(BigDecimal totalAmountRsd) {
        return totalAmountRsd.divide(ethRsdRate, 18, RoundingMode.HALF_UP);
    }

    private void validateCryptoConfiguration() {
        if (merchantAddress == null || merchantAddress.isBlank()) {
            throw new ConflictException("CRYPTO_MERCHANT_ADDRESS is not configured.");
        }
        if (ethRsdRate == null || ethRsdRate.signum() <= 0) {
            throw new ConflictException("ETH_RSD_RATE must be greater than zero.");
        }
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
