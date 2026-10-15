package com.riwi.skillbridge.application.port.in.userpreference;

import com.riwi.skillbridge.application.model.UserPreferenceView;
import com.riwi.skillbridge.domain.model.UserPreference;

import java.util.List;
import java.util.UUID;

public interface ManageUserPreferenceUseCase {
    UserPreference assignPreference(UserPreference userPreference);
    List<UserPreference> getPreferencesByUser(UUID userId);
    void removePreference(UUID userId, UUID preferenceId);
    List<UserPreferenceView> getAllPreferencesWithAssignment(UUID userId);
}
