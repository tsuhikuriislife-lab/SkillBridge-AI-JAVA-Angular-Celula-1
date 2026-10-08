package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.model.OfferingCommand;
import com.riwi.skillbridge.application.model.OfferingPage;
import com.riwi.skillbridge.application.model.OfferingSort;
import com.riwi.skillbridge.application.port.out.CategoryPort;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.OfferingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderOfferingServiceTest {

    private static final String EMAIL = "proveedor@skillbridge.com";

    @Mock
    private OfferingRepositoryPort repository;
    @Mock
    private UserAccountPort users;
    @Mock
    private CategoryPort categories;
    @Mock
    private OfferingCachePort cache;

    @InjectMocks
    private ProviderOfferingService service;

    private final UUID providerId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    private OfferingCommand validCommand() {
        return new OfferingCommand("  Mentoría Java  ", categoryId, new BigDecimal("50000.00"),
                "Mentoría uno a uno", null, null, null, 10);
    }

    private OfferingCommand commandWithName(String name) {
        return new OfferingCommand(name, categoryId, new BigDecimal("50000"), null, null, null, null, null);
    }

    private OfferingCommand commandWithPrice(BigDecimal price) {
        return new OfferingCommand("Mentoría", categoryId, price, null, null, null, null, null);
    }

    private Offering offering(UUID id, UUID owner, OfferingStatus status) {
        return new Offering(id, "SRV-ABC12345", "Mentoría", categoryId, BigDecimal.TEN,
                "Corta", null, null, null, null, status, owner);
    }

    private void providerExists() {
        when(users.findIdByEmail(EMAIL)).thenReturn(Optional.of(providerId));
    }

    // ---------- crear ----------

    @Test
    void create_savesActiveOfferingOwnedByProviderAndEvictsCache() {
        providerExists();
        when(categories.existsById(categoryId)).thenReturn(true);
        when(repository.existsByCode(anyString())).thenReturn(false);
        when(repository.save(any(Offering.class))).thenAnswer(inv -> inv.getArgument(0));

        Offering result = service.create(validCommand(), EMAIL);

        assertEquals(OfferingStatus.ACTIVE, result.status());
        assertEquals(providerId, result.createdBy());
        assertEquals("Mentoría Java", result.name());
        assertTrue(result.code().startsWith("SRV-"));
        verify(repository).save(any(Offering.class));
        verify(cache).evictActiveOfferings();
    }

    @Test
    void create_userNotFound_throwsNotFound() {
        when(users.findIdByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class, () -> service.create(validCommand(), EMAIL));

        verify(repository, never()).save(any());
        verify(cache, never()).evictActiveOfferings();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void create_blankName_throwsAndSavesNothing(String name) {
        providerExists();

        assertThrows(BusinessRuleException.class, () -> service.create(commandWithName(name), EMAIL));

        verify(repository, never()).save(any());
        verify(cache, never()).evictActiveOfferings();
    }

    @Test
    void create_nameTooLong_throws() {
        providerExists();

        assertThrows(BusinessRuleException.class, () -> service.create(commandWithName("a".repeat(161)), EMAIL));

        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "10.999", "10000000000"})
    void create_invalidPrice_throws(String price) {
        providerExists();

        assertThrows(BusinessRuleException.class,
                () -> service.create(commandWithPrice(new BigDecimal(price)), EMAIL));

        verify(repository, never()).save(any());
        verify(cache, never()).evictActiveOfferings();
    }

    @Test
    void create_nullPrice_throws() {
        providerExists();

        assertThrows(BusinessRuleException.class, () -> service.create(commandWithPrice(null), EMAIL));
    }

    @Test
    void create_nonExistentCategory_throws() {
        providerExists();
        when(categories.existsById(categoryId)).thenReturn(false);

        assertThrows(BusinessRuleException.class, () -> service.create(validCommand(), EMAIL));

        verify(repository, never()).save(any());
        verify(cache, never()).evictActiveOfferings();
    }

    @Test
    void create_invalidCapacity_throws() {
        providerExists();
        when(categories.existsById(categoryId)).thenReturn(true);
        OfferingCommand command = new OfferingCommand("Mentoría", categoryId, BigDecimal.TEN,
                null, null, null, null, 0);

        assertThrows(BusinessRuleException.class, () -> service.create(command, EMAIL));

        verify(repository, never()).save(any());
    }

    @Test
    void create_cannotGenerateUniqueCode_throws() {
        providerExists();
        when(categories.existsById(categoryId)).thenReturn(true);
        when(repository.existsByCode(anyString())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.create(validCommand(), EMAIL));

        verify(repository, never()).save(any());
    }

    // ---------- editar ----------

    @Test
    void update_ownOffering_keepsCodeStatusAndOwnerAndEvictsCache() {
        UUID id = UUID.randomUUID();
        providerExists();
        when(repository.findById(id)).thenReturn(Optional.of(offering(id, providerId, OfferingStatus.INACTIVE)));
        when(categories.existsById(categoryId)).thenReturn(true);
        when(repository.save(any(Offering.class))).thenAnswer(inv -> inv.getArgument(0));

        Offering result = service.update(id, validCommand(), EMAIL);

        assertEquals("SRV-ABC12345", result.code());
        assertEquals(OfferingStatus.INACTIVE, result.status());
        assertEquals(providerId, result.createdBy());
        assertEquals("Mentoría Java", result.name());
        verify(cache).evictActiveOfferings();
    }

    @Test
    void update_someoneElsesOffering_throwsForbiddenAndSavesNothing() {
        UUID id = UUID.randomUUID();
        providerExists();
        when(repository.findById(id)).thenReturn(Optional.of(offering(id, UUID.randomUUID(), OfferingStatus.ACTIVE)));

        assertThrows(ForbiddenOperationException.class, () -> service.update(id, validCommand(), EMAIL));

        verify(repository, never()).save(any());
        verify(cache, never()).evictActiveOfferings();
    }

    @Test
    void update_offeringNotFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        providerExists();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class, () -> service.update(id, validCommand(), EMAIL));

        verify(repository, never()).save(any());
    }

    @Test
    void update_invalidData_throwsAndSavesNothing() {
        UUID id = UUID.randomUUID();
        providerExists();
        when(repository.findById(id)).thenReturn(Optional.of(offering(id, providerId, OfferingStatus.ACTIVE)));

        assertThrows(BusinessRuleException.class, () -> service.update(id, commandWithName(" "), EMAIL));

        verify(repository, never()).save(any());
        verify(cache, never()).evictActiveOfferings();
    }

    // ---------- activar / desactivar ----------

    @Test
    void changeStatus_ownOffering_savesNewStatusAndEvictsCache() {
        UUID id = UUID.randomUUID();
        providerExists();
        when(repository.findById(id)).thenReturn(Optional.of(offering(id, providerId, OfferingStatus.ACTIVE)));
        when(repository.save(any(Offering.class))).thenAnswer(inv -> inv.getArgument(0));

        Offering result = service.changeStatus(id, OfferingStatus.INACTIVE, EMAIL);

        assertEquals(OfferingStatus.INACTIVE, result.status());
        verify(cache).evictActiveOfferings();
    }

    @Test
    void changeStatus_sameStatus_isIdempotentAndDoesNotTouchCache() {
        UUID id = UUID.randomUUID();
        Offering current = offering(id, providerId, OfferingStatus.ACTIVE);
        providerExists();
        when(repository.findById(id)).thenReturn(Optional.of(current));

        Offering result = service.changeStatus(id, OfferingStatus.ACTIVE, EMAIL);

        assertSame(current, result);
        verify(repository, never()).save(any());
        verify(cache, never()).evictActiveOfferings();
    }

    @Test
    void changeStatus_someoneElsesOffering_throwsForbidden() {
        UUID id = UUID.randomUUID();
        providerExists();
        when(repository.findById(id)).thenReturn(Optional.of(offering(id, UUID.randomUUID(), OfferingStatus.ACTIVE)));

        assertThrows(ForbiddenOperationException.class,
                () -> service.changeStatus(id, OfferingStatus.INACTIVE, EMAIL));

        verify(repository, never()).save(any());
        verify(cache, never()).evictActiveOfferings();
    }

    @Test
    void changeStatus_nullStatus_throws() {
        assertThrows(BusinessRuleException.class, () -> service.changeStatus(UUID.randomUUID(), null, EMAIL));

        verify(repository, never()).save(any());
    }

    // ---------- listar mis servicios ----------

    @Test
    void listMine_nullSort_usesNameAsc() {
        OfferingPage page = new OfferingPage(List.of(), 0, 10, 0, 0);
        providerExists();
        when(repository.findPageByProviderId(providerId, 0, 10, OfferingSort.NAME_ASC)).thenReturn(page);

        assertSame(page, service.listMine(EMAIL, 0, 10, null));
    }

    @Test
    void listMine_passesRequestedSort() {
        OfferingPage page = new OfferingPage(List.of(), 1, 5, 0, 0);
        providerExists();
        when(repository.findPageByProviderId(providerId, 1, 5, OfferingSort.CREATED_DESC)).thenReturn(page);

        assertSame(page, service.listMine(EMAIL, 1, 5, OfferingSort.CREATED_DESC));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    void listMine_invalidSize_throws(int size) {
        assertThrows(BusinessRuleException.class, () -> service.listMine(EMAIL, 0, size, null));
    }

    @Test
    void listMine_negativePage_throws() {
        assertThrows(BusinessRuleException.class, () -> service.listMine(EMAIL, -1, 10, null));
    }
}