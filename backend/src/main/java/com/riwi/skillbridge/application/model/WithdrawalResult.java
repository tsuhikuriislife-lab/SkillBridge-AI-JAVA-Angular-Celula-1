package com.riwi.skillbridge.application.model;

import com.riwi.skillbridge.domain.model.ServiceEnrollment;

public record WithdrawalResult(
        ServiceEnrollment enrollment,
        boolean refund       // true = se reembolsa (retiro dentro de 24h)
) {}
