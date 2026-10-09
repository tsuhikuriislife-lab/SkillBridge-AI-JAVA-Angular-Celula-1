package com.riwi.skillbridge.application.service    ;

import com.riwi.skillbridge.application.model.UserPreferenceView;
import com.riwi.skillbridge.application.port.in.userpreference.ManageUserPreferenceUseCase;
import com.riwi.skillbridge.application.port.out.CatalogItemPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.application.port.out.UserPreferencePort;
import com.riwi.skillbridge.domain.enums.CatalogType;
import com.riwi.skillbridge.domain.enums.Status;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.CatalogItem;
import com.riwi.skillbridge.domain.model.UserPreference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserPreferenceService implements ManageUserPreferenceUseCase {

    private final UserPreferencePort userPreferencePort;
    private final UserAccountPort userAccountPort;    // para REGLA 5
    private final CatalogItemPort catalogItemPort;    // para REGLAS 3 y 4

    public UserPreferenceService(UserPreferencePort userPreferencePort,
                                 UserAccountPort userAccountPort,
                                 CatalogItemPort catalogItemPort) {
        this.userPreferencePort = userPreferencePort;
        this.userAccountPort = userAccountPort;
        this.catalogItemPort = catalogItemPort;
    }

    @Override
    public UserPreference assignPreference(UserPreference userPreference) {
        // 🛡️ REGLA 5: el usuario debe existir
        if (userAccountPort.findById(userPreference.userId()).isEmpty()) {
            throw new BusinessRuleException("El usuario no existe: " + userPreference.userId());
        }

        // 🛡️ REGLAS 3 y 4: el item debe existir, ser PREFERENCE y estar ACTIVE
        CatalogItem item = catalogItemPort.findById(userPreference.preferenceId())
            .orElseThrow(() -> new BusinessRuleException("La preferencia no existe: " + userPreference.preferenceId()));
        if (item.type() != CatalogType.PREFERENCE) {
            throw new BusinessRuleException("El item '" + item.name() + "' no es una preferencia (es " + item.type() + ")");
        }
        if (item.status() != Status.ACTIVE) {
            throw new BusinessRuleException("La preferencia '" + item.name() + "' está desactivada y no se puede asignar");
        }

        // 🛡️ REGLA 2: si ya existe, solo se reactiva; si no, se crea ACTIVE
        return userPreferencePort.findByUserIdAndPreferenceId(
                userPreference.userId(), userPreference.preferenceId())
            .map(existing -> userPreferencePort.save(new UserPreference(
                existing.userId(),
                existing.preferenceId(),
                Status.ACTIVE          // reactivar
            )))
            .orElseGet(() -> userPreferencePort.save(new UserPreference(
                userPreference.userId(),
                userPreference.preferenceId(),
                Status.ACTIVE          // nueva asignación
            )));
    }

    @Override
    public List<UserPreference> getPreferencesByUser(UUID userId) {
        // Regla 1: el perfil muestra solo las activas
        return userPreferencePort.findByUserId(userId).stream()
            .filter(p -> p.status() == Status.ACTIVE)
            .toList();
    }

    @Override
    public void removePreference(UUID userId, UUID preferenceId) {
        // 🛡️ REGLA 6: no se borra, se desactiva
        userPreferencePort.findByUserIdAndPreferenceId(userId, preferenceId)
            .ifPresent(existing -> userPreferencePort.save(new UserPreference(
                existing.userId(),
                existing.preferenceId(),
                Status.INACTIVE
            )));
    }

    @Override
    public List<UserPreferenceView> getAllPreferencesWithAssignment(UUID userId) {
        return userPreferencePort.findAllPreferencesWithAssignment(userId);
    }
}
