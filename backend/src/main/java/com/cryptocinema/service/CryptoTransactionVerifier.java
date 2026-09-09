package com.cryptocinema.service;

import java.math.BigDecimal;

public interface CryptoTransactionVerifier {

    CryptoTransactionVerification verify(
            String transactionHash,
            String merchantAddress,
            BigDecimal expectedCryptoAmount,
            Long expectedChainId
    );
}
