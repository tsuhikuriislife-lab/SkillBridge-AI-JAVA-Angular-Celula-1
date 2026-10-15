package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.model.UserPreferenceView;
import com.riwi.skillbridge.application.port.out.UserPreferencePort;
import com.riwi.skillbridge.domain.model.UserPreference;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.UserPreferenceEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.UserPreferenceId;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaUserPreferenceRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class UserPreferencePersistenceAdapter implements UserPreferencePort {

    private final JpaUserPreferenceRepository repository;

    public UserPreferencePersistenceAdapter(JpaUserPreferenceRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserPreference save(UserPreference userPreference) {
        return toDomain(repository.save(toEntity(userPreference)));
    }

    @Override
    public Optional<UserPreference> findByUserIdAndPreferenceId(UUID userId, UUID preferenceId) {
        return repository.findById(new UserPreferenceId(userId, preferenceId)).map(this::toDomain);
    }

    @Override
    public List<UserPreference> findByUserId(UUID userId) {
        return repository.findByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public void delete(UUID userId, UUID preferenceId) {
        repository.deleteById(new UserPreferenceId(userId, preferenceId));
    }

    private UserPreferenceEntity toEntity(UserPreference p) {
        return new UserPreferenceEntity(p.userId(), p.preferenceId(), p.status());
    }

    private UserPreference toDomain(UserPreferenceEntity e) {
        return new UserPreference(e.getUserId(), e.getPreferenceId(), e.getStatus());
    }

    @Override
    public List<UserPreferenceView> findAllPreferencesWithAssignment(UUID userId) {
        return repository.findAllPreferencesWithAssignment(userId);
    }
}
