package com.cryptocinema.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class JsonRpcCryptoTransactionVerifier implements CryptoTransactionVerifier {

    private static final BigDecimal WEI_IN_ETH = new BigDecimal("1000000000000000000");

    private final RestTemplate restTemplate = new RestTemplate();
    private final String web3RpcUrl;

    public JsonRpcCryptoTransactionVerifier(@Value("${app.crypto.web3-rpc-url:}") String web3RpcUrl) {
        this.web3RpcUrl = web3RpcUrl;
    }

    @Override
    public CryptoTransactionVerification verify(
            String transactionHash,
            String merchantAddress,
            BigDecimal expectedCryptoAmount,
            Long expectedChainId
    ) {
        if (web3RpcUrl == null || web3RpcUrl.isBlank()) {
            return CryptoTransactionVerification.failed("WEB3_RPC_URL is not configured.");
        }

        try {
            Long chainId = hexToLong(call("eth_chainId").result());
            if (!expectedChainId.equals(chainId)) {
                return CryptoTransactionVerification.failed("Transaction verifier is not connected to the expected test network.");
            }

            Object transactionResult = call("eth_getTransactionByHash", transactionHash).result();
            if (!(transactionResult instanceof Map<?, ?> transaction)) {
                return CryptoTransactionVerification.pending("Transaction was not found yet.");
            }

            String to = stringValue(transaction.get("to"));
            if (to == null || !to.equalsIgnoreCase(merchantAddress)) {
                return CryptoTransactionVerification.failed("Transaction recipient does not match merchant wallet.");
            }

            BigInteger actualWei = hexToBigInteger(stringValue(transaction.get("value")));
            BigInteger expectedWei = expectedCryptoAmount.multiply(WEI_IN_ETH).setScale(0, java.math.RoundingMode.HALF_UP).toBigIntegerExact();
            if (actualWei.compareTo(expectedWei) < 0) {
                return CryptoTransactionVerification.failed("Transaction value is lower than expected amount.");
            }

            Object receiptResult = call("eth_getTransactionReceipt", transactionHash).result();
            if (!(receiptResult instanceof Map<?, ?> receipt)) {
                return CryptoTransactionVerification.pending("Transaction is waiting for confirmation.");
            }

            String status = stringValue(receipt.get("status"));
            if ("0x1".equalsIgnoreCase(status)) {
                return CryptoTransactionVerification.success();
            }
            return CryptoTransactionVerification.failed("Transaction receipt status is failed.");
        } catch (RestClientException | IllegalArgumentException exception) {
            return CryptoTransactionVerification.failed("Transaction verification failed.");
        }
    }

    private RpcResponse call(String method, Object... params) {
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", method,
                "params", params);
        return restTemplate.postForObject(web3RpcUrl, request, RpcResponse.class);
    }

    private Long hexToLong(Object value) {
        return hexToBigInteger(String.valueOf(value)).longValueExact();
    }

    private BigInteger hexToBigInteger(String value) {
        if (value == null || !value.toLowerCase(Locale.ROOT).startsWith("0x")) {
            throw new IllegalArgumentException("Invalid hex value");
        }
        return new BigInteger(value.substring(2), 16);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record RpcResponse(Object result) {
    }
}
