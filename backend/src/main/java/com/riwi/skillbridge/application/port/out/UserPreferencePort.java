package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.application.model.UserPreferenceView;
import com.riwi.skillbridge.domain.model.UserPreference;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserPreferencePort {

    UserPreference save(UserPreference userPreference);

    Optional<UserPreference> findByUserIdAndPreferenceId(UUID userId, UUID preferenceId);

    List<UserPreference> findByUserId(UUID userId);

    void delete(UUID userId, UUID preferenceId);

    List<UserPreferenceView> findAllPreferencesWithAssignment(UUID userId);
}
