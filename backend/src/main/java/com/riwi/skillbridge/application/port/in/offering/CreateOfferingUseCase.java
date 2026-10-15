package com.riwi.skillbridge.application.port.in.offering;

import com.riwi.skillbridge.domain.model.Offering;

public interface CreateOfferingUseCase {
    Offering createOffering(Offering offering);
}
