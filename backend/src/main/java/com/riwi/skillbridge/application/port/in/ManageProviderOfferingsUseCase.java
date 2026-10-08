package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.application.model.OfferingCommand;
import com.riwi.skillbridge.application.model.OfferingPage;
import com.riwi.skillbridge.application.model.OfferingSort;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.OfferingStatus;

import java.util.UUID;

public interface ManageProviderOfferingsUseCase {
    Offering create(OfferingCommand command, String  providerEmail);

    Offering update(UUID offeringId, OfferingCommand command, String providerEmail);

    Offering changeStatus(UUID offeringId, OfferingStatus status, String providerEmail);

    OfferingPage listMine(String providerEmail, int page, int size, OfferingSort sort );
}
