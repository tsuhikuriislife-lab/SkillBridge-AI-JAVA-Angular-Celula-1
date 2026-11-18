package com.riwi.skillbridge.domain.exception;

/** El actor no tiene permiso para modificar la cuenta indicada (se traduce a HTTP 403). */
public class AccountAccessDeniedException extends BusinessRuleException {
    public AccountAccessDeniedException(String message) {
        super(message);
    }
}
