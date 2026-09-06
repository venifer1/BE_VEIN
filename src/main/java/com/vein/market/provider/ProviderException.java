package com.vein.market.provider;

/**
 * Generic failure talking to an external market-data provider. Mapped to a
 * retryable exception per provider in resilience4j config.
 */
public class ProviderException extends RuntimeException {

    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProviderException(String message) {
        super(message);
    }
}
