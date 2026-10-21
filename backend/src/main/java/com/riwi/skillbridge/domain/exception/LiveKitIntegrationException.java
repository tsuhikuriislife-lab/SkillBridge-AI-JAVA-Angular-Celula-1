package com.riwi.skillbridge.domain.exception;

/**
 * Fallo de comunicación o de API en la integración con LiveKit.
 */
public class LiveKitIntegrationException extends RuntimeException {
    public LiveKitIntegrationException(String message) {
        super(message);
    }
}
