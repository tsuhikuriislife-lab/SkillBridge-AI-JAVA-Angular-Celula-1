package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.application.model.FeaturesUpdatedEvent;

public interface HandleFeaturesUpdatedUseCase {
    void handle(FeaturesUpdatedEvent event);
}
