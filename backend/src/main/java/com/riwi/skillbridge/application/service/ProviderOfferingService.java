package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.model.OfferingCommand;
import com.riwi.skillbridge.application.model.OfferingPage;
import com.riwi.skillbridge.application.model.OfferingSort;
import com.riwi.skillbridge.application.port.in.ManageProviderOfferingsUseCase;
import com.riwi.skillbridge.application.port.out.CategoryPort;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.OfferingStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ProviderOfferingService implements ManageProviderOfferingsUseCase {
    private static final int MAX_NAME_LENGTH = 160;
    private static final int MAX_SHORT_DESCRIPTION_LENGTH = 500;
    private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999.99");
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final OfferingRepositoryPort repository;
    private final UserAccountPort users;
    private final CategoryPort categories;
    private final OfferingCachePort cache;

    public ProviderOfferingService(OfferingRepositoryPort repository,
                                   UserAccountPort users,
                                   CategoryPort categories,
                                   OfferingCachePort cache) {
        this.repository = repository;
        this.users = users;
        this.categories = categories;
        this.cache = cache;
    }

    @Override
    public Offering create(OfferingCommand command, String providerEmail) {
        UUID providerId = providerIdOf(providerEmail);
        validate(command);

        Offering offering = new Offering(
                UUID.randomUUID(), generateCode(), command.name().trim(), command.categoryId(), command.price(),
                clean(command.shortDescription()), clean(command.detail()),
                clean(command.learningObjectives()), clean(command.prerequisites()),
                command.capacity(), OfferingStatus.ACTIVE, providerId);

        Offering saved = repository.save(offering);
        cache.evictActiveOfferings();
        return saved;
    }

    @Override
    public Offering update(UUID offeringId, OfferingCommand command, String providerEmail) {
        UUID providerId = providerIdOf(providerEmail);
        Offering current = ownedOffering(offeringId, providerId);
        validate(command);

        Offering updated = new Offering(
                current.id(), current.code(), command.name().trim(), command.categoryId(), command.price(),
                clean(command.shortDescription()), clean(command.detail()),
                clean(command.learningObjectives()), clean(command.prerequisites()),
                command.capacity(), current.status(), current.createdBy());

        Offering saved = repository.save(updated);
        cache.evictActiveOfferings();
        return saved;
    }

    @Override
    public Offering changeStatus(UUID offeringId, OfferingStatus status, String providerEmail) {
        if (status == null) {
            throw new BusinessRuleException("El estado es obligatorio");
        }
        UUID providerId = providerIdOf(providerEmail);
        Offering current = ownedOffering(offeringId, providerId);
        if (current.status() == status) {
            return current;
        }

        Offering changed = new Offering(
                current.id(), current.code(), current.name(), current.categoryId(), current.price(),
                current.shortDescription(), current.detail(), current.learningObjectives(),
                current.prerequisites(), current.capacity(), status, current.createdBy());

        Offering saved = repository.save(changed);
        cache.evictActiveOfferings();
        return saved;
    }

    @Override
    public OfferingPage listMine(String providerEmail, int page, int size, OfferingSort sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessRuleException("La página no puede ser negativa y el tamaño debe estar entre 1 y 100");
        }
        UUID providerId = providerIdOf(providerEmail);
        return repository.findPageByProviderId(providerId, page, size, sort == null ? OfferingSort.NAME_ASC : sort);
    }

    private UUID providerIdOf(String providerEmail) {
        return users.findIdByEmail(providerEmail)
                .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));
    }

    private Offering ownedOffering(UUID offeringId, UUID providerId) {
        Offering offering = repository.findById(offeringId)
                .orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado"));
        if (!providerId.equals(offering.createdBy())) {
            throw new ForbiddenOperationException("No puedes modificar un servicio que no es tuyo");
        }
        return offering;
    }

    private void validate(OfferingCommand command) {
        if (command == null) {
            throw new BusinessRuleException("Los datos del servicio son obligatorios");
        }
        if (command.name() == null || command.name().isBlank()) {
            throw new BusinessRuleException("El nombre es obligatorio");
        }
        if (command.name().trim().length() > MAX_NAME_LENGTH) {
            throw new BusinessRuleException("El nombre no puede superar " + MAX_NAME_LENGTH + " caracteres");
        }
        if (command.price() == null || command.price().signum() <= 0) {
            throw new BusinessRuleException("El precio debe ser mayor que cero");
        }
        if (command.price().compareTo(MAX_PRICE) > 0 || command.price().stripTrailingZeros().scale() > 2) {
            throw new BusinessRuleException("El precio no es válido: máximo dos decimales y menor a 10.000.000.000");
        }
        if (command.categoryId() == null) {
            throw new BusinessRuleException("La categoría es obligatoria");
        }
        if (!categories.existsById(command.categoryId())) {
            throw new BusinessRuleException("La categoría no existe");
        }
        if (command.shortDescription() != null && command.shortDescription().length() > MAX_SHORT_DESCRIPTION_LENGTH) {
            throw new BusinessRuleException(
                    "La descripción corta no puede superar " + MAX_SHORT_DESCRIPTION_LENGTH + " caracteres");
        }
        if (command.capacity() != null && command.capacity() < 1) {
            throw new BusinessRuleException("La capacidad debe ser mayor que cero");
        }
    }

    private String generateCode() {
        for (int i = 0; i < MAX_CODE_ATTEMPTS; i++) {
            String code = "SRV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
            if (!repository.existsByCode(code)) {
                return code;
            }
        }
        throw new BusinessRuleException("No fue posible generar un código único, intenta de nuevo");
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}