package com.cryptocinema.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CryptoPaymentConfirmRequest(
        @NotNull Long paymentId,
        @NotBlank String transactionHash,
        @NotBlank String walletAddress
) {
}
