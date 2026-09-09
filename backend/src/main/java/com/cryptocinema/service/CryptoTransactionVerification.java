package com.cryptocinema.service;

public record CryptoTransactionVerification(
        CryptoTransactionVerificationStatus status,
        String message
) {

    public static CryptoTransactionVerification success() {
        return new CryptoTransactionVerification(CryptoTransactionVerificationStatus.SUCCESS, "Transaction verified.");
    }

    public static CryptoTransactionVerification pending(String message) {
        return new CryptoTransactionVerification(CryptoTransactionVerificationStatus.PENDING, message);
    }

    public static CryptoTransactionVerification failed(String message) {
        return new CryptoTransactionVerification(CryptoTransactionVerificationStatus.FAILED, message);
    }
}
