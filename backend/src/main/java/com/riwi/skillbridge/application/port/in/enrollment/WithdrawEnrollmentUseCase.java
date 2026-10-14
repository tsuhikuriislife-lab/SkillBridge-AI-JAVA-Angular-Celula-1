package com.riwi.skillbridge.application.port.in.enrollment;

import com.riwi.skillbridge.application.model.WithdrawalResult;

import java.util.UUID;

public interface WithdrawEnrollmentUseCase {
    WithdrawalResult withdraw(UUID userId, UUID serviceId); // <24h desde la inscripción → refund=true
}
