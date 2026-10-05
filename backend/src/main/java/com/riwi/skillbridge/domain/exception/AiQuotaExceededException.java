package com.riwi.skillbridge.domain.exception;

public class AiQuotaExceededException extends RuntimeException {
    public AiQuotaExceededException(String message) {
        super(message);
    }
}
