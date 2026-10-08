package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.model.OfferingCommand;
import com.riwi.skillbridge.application.model.OfferingPage;
import com.riwi.skillbridge.application.model.OfferingSort;
import com.riwi.skillbridge.application.port.in.ManageProviderOfferingsUseCase;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.OfferingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProviderOfferingControllerTest {

    private static final String EMAIL = "proveedor@skillbridge.com";
    private static final String BASE = "/api/provider/offerings";

    @Mock
    private ManageProviderOfferingsUseCase useCase;

    private MockMvc mvc;
    private final UUID categoryId = UUID.randomUUID();
    private final UUID offeringId = UUID.randomUUID();
    private final UsernamePasswordAuthenticationToken principal =
            new UsernamePasswordAuthenticationToken(EMAIL, null);

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProviderOfferingController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String validBody() {
        return """
                {"name":"Mentoría Java","categoryId":"%s","price":50000,"shortDescription":"Uno a uno","capacity":5}
                """.formatted(categoryId);
    }

    private Offering offering(OfferingStatus status) {
        return new Offering(offeringId, "SRV-ABC12345", "Mentoría Java", categoryId, new BigDecimal("50000"),
                "Uno a uno", null, null, null, 5, status, UUID.randomUUID());
    }

    // ---------- crear ----------

    @Test
    void create_validBody_returns201AndUsesAuthenticatedEmail() throws Exception {
        when(useCase.create(any(OfferingCommand.class), eq(EMAIL))).thenReturn(offering(OfferingStatus.ACTIVE));

        mvc.perform(post(BASE).principal(principal).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Mentoría Java"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void create_blankName_returns400AndDoesNotCallUseCase() throws Exception {
        String body = """
                {"name":"  ","categoryId":"%s","price":50000}
                """.formatted(categoryId);

        mvc.perform(post(BASE).principal(principal).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verify(useCase, never()).create(any(), any());
    }

    @Test
    void create_missingPrice_returns400() throws Exception {
        String body = """
                {"name":"Mentoría","categoryId":"%s"}
                """.formatted(categoryId);

        mvc.perform(post(BASE).principal(principal).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_businessRuleViolation_returns422() throws Exception {
        when(useCase.create(any(OfferingCommand.class), eq(EMAIL)))
                .thenThrow(new BusinessRuleException("La categoría no existe"));

        mvc.perform(post(BASE).principal(principal).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("La categoría no existe"));
    }

    // ---------- editar ----------

    @Test
    void update_someoneElsesOffering_returns403WithMessage() throws Exception {
        when(useCase.update(eq(offeringId), any(OfferingCommand.class), eq(EMAIL)))
                .thenThrow(new ForbiddenOperationException("No puedes modificar un servicio que no es tuyo"));

        mvc.perform(put(BASE + "/" + offeringId).principal(principal)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No puedes modificar un servicio que no es tuyo"));
    }

    @Test
    void update_notFound_returns404() throws Exception {
        when(useCase.update(eq(offeringId), any(OfferingCommand.class), eq(EMAIL)))
                .thenThrow(new DomainNotFoundException("Servicio no encontrado"));

        mvc.perform(put(BASE + "/" + offeringId).principal(principal)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_ownOffering_returns200() throws Exception {
        when(useCase.update(eq(offeringId), any(OfferingCommand.class), eq(EMAIL)))
                .thenReturn(offering(OfferingStatus.ACTIVE));

        mvc.perform(put(BASE + "/" + offeringId).principal(principal)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(offeringId.toString()));
    }

    // ---------- activar / desactivar ----------

    @Test
    void changeStatus_valid_returns200() throws Exception {
        when(useCase.changeStatus(offeringId, OfferingStatus.INACTIVE, EMAIL))
                .thenReturn(offering(OfferingStatus.INACTIVE));

        mvc.perform(patch(BASE + "/" + offeringId + "/status").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void changeStatus_someoneElsesOffering_returns403() throws Exception {
        when(useCase.changeStatus(offeringId, OfferingStatus.INACTIVE, EMAIL))
                .thenThrow(new ForbiddenOperationException("No puedes modificar un servicio que no es tuyo"));

        mvc.perform(patch(BASE + "/" + offeringId + "/status").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void changeStatus_missingStatus_returns400() throws Exception {
        mvc.perform(patch(BASE + "/" + offeringId + "/status").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verify(useCase, never()).changeStatus(any(), any(), any());
    }

    // ---------- listar mis servicios ----------

    @Test
    void listMine_withoutParams_usesDefaults() throws Exception {
        when(useCase.listMine(EMAIL, 0, 10, OfferingSort.NAME_ASC))
                .thenReturn(new OfferingPage(List.of(offering(OfferingStatus.ACTIVE)), 0, 10, 1, 1));

        mvc.perform(get(BASE).principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Mentoría Java"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listMine_withParams_passesThemToUseCase() throws Exception {
        when(useCase.listMine(EMAIL, 2, 5, OfferingSort.CREATED_DESC))
                .thenReturn(new OfferingPage(List.of(), 2, 5, 0, 0));

        mvc.perform(get(BASE).principal(principal)
                        .param("page", "2").param("size", "5").param("sort", "CREATED_DESC"))
                .andExpect(status().isOk());

        verify(useCase).listMine(EMAIL, 2, 5, OfferingSort.CREATED_DESC);
    }

    @Test
    void listMine_invalidSort_returns400() throws Exception {
        mvc.perform(get(BASE).principal(principal).param("sort", "INVENTADO"))
                .andExpect(status().isBadRequest());

        verify(useCase, never()).listMine(any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), any());
    }
}