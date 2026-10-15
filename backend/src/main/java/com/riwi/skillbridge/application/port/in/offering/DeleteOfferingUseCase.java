package com.riwi.skillbridge.application.port.in.offering;

import java.util.UUID;

public interface DeleteOfferingUseCase {
    boolean deleteOffering(UUID id);
}
