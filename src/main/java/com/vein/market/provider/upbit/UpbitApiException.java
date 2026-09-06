package com.vein.market.provider.upbit;

/**
 * Raised on Upbit HTTP/transport errors. Referenced as a retry-exception in
 * application.yml ({@code resilience4j.retry.instances.upbit.retry-exceptions}).
 */
public class UpbitApiException extends RuntimeException {

    public UpbitApiException(String message) {
        super(message);
    }

    public UpbitApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
