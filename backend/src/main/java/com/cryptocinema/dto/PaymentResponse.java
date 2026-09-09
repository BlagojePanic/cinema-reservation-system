package com.cryptocinema.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.cryptocinema.entity.PaymentMethod;
import com.cryptocinema.entity.PaymentStatus;

public record PaymentResponse(
        Long paymentId,
        Long reservationId,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        PaymentStatus status,
        String reference,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        String cryptoCurrency,
        BigDecimal cryptoAmount,
        BigDecimal exchangeRate,
        String network,
        Long chainId,
        String walletAddress,
        String transactionHash
) {
}
