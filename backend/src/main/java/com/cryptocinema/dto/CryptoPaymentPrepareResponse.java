package com.cryptocinema.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CryptoPaymentPrepareResponse(
        Long reservationId,
        Long paymentId,
        String merchantAddress,
        String network,
        Long chainId,
        String cryptoCurrency,
        BigDecimal cryptoAmount,
        BigDecimal amountRsd,
        LocalDateTime expiresAt
) {
}
