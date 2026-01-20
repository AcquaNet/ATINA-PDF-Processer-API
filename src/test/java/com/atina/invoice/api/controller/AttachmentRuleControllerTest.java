package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.request.CreateAttachmentRuleRequest;
import com.atina.invoice.api.dto.request.UpdateAttachmentRuleRequest;
import com.atina.invoice.api.dto.response.AttachmentProcessingRuleResponse;
import com.atina.invoice.api.security.JwtTokenProvider;
import com.atina.invoice.api.security.TenantInterceptor;
import com.atina.invoice.api.service.AttachmentRuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests para AttachmentRuleController
 *
 * SOLUCIÓN AL PROBLEMA DE SEGURIDAD:
 * - @WebMvcTest: Solo carga el controller
 * - @AutoConfigureMockMvc(addFilters = false): Deshabilita filtros de seguridad
 * - @MockBean para JwtTokenProvider y TenantInterceptor: Evita errores de dependencias
 * - @WithMockUser: Simula usuario autenticado (opcional, pero recomendado)
 */
@WebMvcTest(AttachmentRuleController.class)
@AutoConfigureMockMvc(addFilters = false)  //  Deshabilita filtros de seguridad
@DisplayName("Attachment Rule Controller Tests")
class AttachmentRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AttachmentRuleService attachmentRuleService;

    // ⭐ Mock de beans de seguridad para evitar errores de dependencias
    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private TenantInterceptor tenantInterceptor;

    private AttachmentProcessingRuleResponse sampleResponse;
    private CreateAttachmentRuleRequest createRequest;

    @BeforeEach
    void setUp() {
        // Sample response
        sampleResponse = AttachmentProcessingRuleResponse.builder()
                .id(1L)
                .senderRuleId(1L)
                .ruleOrder(1)
                .fileNameRegex("^Invoice+([0-9])+(.PDF|.pdf)$")
                .source("invoice")
                .destination("jde")
                .processingMethod("")
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Create request
        createRequest = CreateAttachmentRuleRequest.builder()
                .ruleOrder(1)
                .fileNameRegex("^Invoice+([0-9])+(.PDF|.pdf)$")
                .source("invoice")
                .destination("jde")
                .enabled(true)
                .build();
    }

    // ========== GET /api/v1/sender-rules/{senderRuleId}/attachment-rules ==========

    @Test
    @DisplayName("GET /sender-rules/{id}/attachment-rules - Should return all attachment rules")
    void getRulesBySenderRule_ValidId_ShouldReturnRules() throws Exception {
        // Given
        List<AttachmentProcessingRuleResponse> rules = Arrays.asList(sampleResponse);
        when(attachmentRuleService.getRulesBySenderRule(1L)).thenReturn(rules);

        // When & Then
        mockMvc.perform(get("/api/v1/sender-rules/1/attachment-rules")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].ruleOrder").value(1))
                .andExpect(jsonPath("$.data[0].fileNameRegex").value("^Invoice+([0-9])+(.PDF|.pdf)$"))
                .andExpect(jsonPath("$.data[0].source").value("invoice"))
                .andExpect(jsonPath("$.data[0].destination").value("jde"));

        verify(attachmentRuleService, times(1)).getRulesBySenderRule(1L);
    }

    @Test
    @DisplayName("GET /sender-rules/{id}/attachment-rules - Should return empty array when no rules")
    void getRulesBySenderRule_NoRules_ShouldReturnEmptyArray() throws Exception {
        // Given
        when(attachmentRuleService.getRulesBySenderRule(1L)).thenReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/api/v1/sender-rules/1/attachment-rules")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(attachmentRuleService, times(1)).getRulesBySenderRule(1L);
    }

    // ========== GET /api/v1/attachment-rules/{id} ==========

    @Test
    @DisplayName("GET /attachment-rules/{id} - Should return rule by ID")
    void getRuleById_ValidId_ShouldReturnRule() throws Exception {
        // Given
        when(attachmentRuleService.getRuleById(1L)).thenReturn(sampleResponse);

        // When & Then
        mockMvc.perform(get("/api/v1/attachment-rules/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.fileNameRegex").value("^Invoice+([0-9])+(.PDF|.pdf)$"));

        verify(attachmentRuleService, times(1)).getRuleById(1L);
    }

    @Test
    @DisplayName("GET /attachment-rules/{id} - Should return 404 when rule not found")
    void getRuleById_NotFound_ShouldReturn404() throws Exception {
        // Given
        when(attachmentRuleService.getRuleById(999L))
                .thenThrow(new RuntimeException("Attachment rule not found: 999"));

        // When & Then
        mockMvc.perform(get("/api/v1/attachment-rules/999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());

        verify(attachmentRuleService, times(1)).getRuleById(999L);
    }

    // ========== POST /api/v1/sender-rules/{senderRuleId}/attachment-rules ==========

    @Test
    @DisplayName("POST /sender-rules/{id}/attachment-rules - Should create rule successfully")
    void createRule_ValidRequest_ShouldCreateRule() throws Exception {
        // Given
        when(attachmentRuleService.createRule(eq(1L), any(CreateAttachmentRuleRequest.class)))
                .thenReturn(sampleResponse);

        // When & Then
        mockMvc.perform(post("/api/v1/sender-rules/1/attachment-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.ruleOrder").value(1))
                .andExpect(jsonPath("$.data.fileNameRegex").value("^Invoice+([0-9])+(.PDF|.pdf)$"));

        verify(attachmentRuleService, times(1)).createRule(eq(1L), any(CreateAttachmentRuleRequest.class));
    }

    @Test
    @DisplayName("POST /sender-rules/{id}/attachment-rules - Should return 400 for missing regex")
    void createRule_MissingRegex_ShouldReturnBadRequest() throws Exception {
        // Given
        createRequest.setFileNameRegex(null);

        // When & Then
        mockMvc.perform(post("/api/v1/sender-rules/1/attachment-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isBadRequest());

        verify(attachmentRuleService, never()).createRule(any(), any());
    }

    // ========== PUT /api/v1/attachment-rules/{id} ==========

    @Test
    @DisplayName("PUT /attachment-rules/{id} - Should update rule successfully")
    void updateRule_ValidRequest_ShouldUpdateRule() throws Exception {
        // Given
        UpdateAttachmentRuleRequest updateRequest = UpdateAttachmentRuleRequest.builder()
                .fileNameRegex("^Invoice+([0-9])+(.pdf)$")
                .enabled(false)
                .build();

        AttachmentProcessingRuleResponse updated = AttachmentProcessingRuleResponse.builder()
                .id(1L)
                .fileNameRegex("^Invoice+([0-9])+(.pdf)$")
                .enabled(false)
                .build();

        when(attachmentRuleService.updateRule(eq(1L), any(UpdateAttachmentRuleRequest.class)))
                .thenReturn(updated);

        // When & Then
        mockMvc.perform(put("/api/v1/attachment-rules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileNameRegex").value("^Invoice+([0-9])+(.pdf)$"))
                .andExpect(jsonPath("$.data.enabled").value(false));

        verify(attachmentRuleService, times(1)).updateRule(eq(1L), any(UpdateAttachmentRuleRequest.class));
    }

    // ========== DELETE /api/v1/attachment-rules/{id} ==========

    @Test
    @DisplayName("DELETE /attachment-rules/{id} - Should delete rule successfully")
    void deleteRule_ValidId_ShouldDeleteRule() throws Exception {
        // Given
        doNothing().when(attachmentRuleService).deleteRule(1L);

        // When & Then
        mockMvc.perform(delete("/api/v1/attachment-rules/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(attachmentRuleService, times(1)).deleteRule(1L);
    }

    // ========== PATCH /api/v1/attachment-rules/{id}/reorder ==========

    @Test
    @DisplayName("PATCH /attachment-rules/{id}/reorder - Should reorder rule successfully")
    void reorderRule_ValidOrder_ShouldReorder() throws Exception {
        // Given
        AttachmentProcessingRuleResponse reordered = AttachmentProcessingRuleResponse.builder()
                .id(1L)
                .ruleOrder(5)
                .build();

        when(attachmentRuleService.reorderRule(1L, 5)).thenReturn(reordered);

        // When & Then
        mockMvc.perform(patch("/api/v1/attachment-rules/1/reorder")
                        .param("newOrder", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ruleOrder").value(5));

        verify(attachmentRuleService, times(1)).reorderRule(1L, 5);
    }

    // ========== POST /api/v1/attachment-rules/test-regex ==========

    @Test
    @DisplayName("POST /attachment-rules/test-regex - Should test regex successfully")
    void testRegex_ValidPattern_ShouldReturnMatches() throws Exception {
        // Given
        String regex = "^Invoice+([0-9])+(.PDF|.pdf)$";
        List<String> filenames = Arrays.asList(
                "Invoice123.pdf",
                "Invoice456.PDF",
                "Report.pdf",
                "Invoice.txt"
        );

        Map<String, Boolean> matches = new HashMap<>();
        matches.put("Invoice123.pdf", true);
        matches.put("Invoice456.PDF", true);
        matches.put("Report.pdf", false);
        matches.put("Invoice.txt", false);

        Map<String, Object> result = new HashMap<>();
        result.put("valid", true);
        result.put("regex", regex);
        result.put("matches", matches);
        result.put("totalFiles", 4);
        result.put("matchedFiles", 2L);

        when(attachmentRuleService.testRegex(regex, filenames)).thenReturn(result);

        // When & Then
        mockMvc.perform(post("/api/v1/attachment-rules/test-regex")
                        .param("regex", regex)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(filenames)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.regex").value(regex))
                .andExpect(jsonPath("$.data.matches['Invoice123.pdf']").value(true))
                .andExpect(jsonPath("$.data.matches['Invoice456.PDF']").value(true))
                .andExpect(jsonPath("$.data.matches['Report.pdf']").value(false))
                .andExpect(jsonPath("$.data.matches['Invoice.txt']").value(false))
                .andExpect(jsonPath("$.data.totalFiles").value(4))
                .andExpect(jsonPath("$.data.matchedFiles").value(2));

        verify(attachmentRuleService, times(1)).testRegex(regex, filenames);
    }

    @Test
    @DisplayName("POST /attachment-rules/test-regex - Should return invalid for bad regex")
    void testRegex_InvalidPattern_ShouldReturnInvalid() throws Exception {
        // Given
        String regex = "^Invoice+([0-9])+(.PDF|.pdf"; // Missing closing bracket
        List<String> filenames = Arrays.asList("Invoice123.pdf");

        Map<String, Object> result = new HashMap<>();
        result.put("valid", false);
        result.put("regex", regex);
        result.put("error", "Unclosed group near index 28");
        result.put("errorIndex", 28);

        when(attachmentRuleService.testRegex(regex, filenames)).thenReturn(result);

        // When & Then
        mockMvc.perform(post("/api/v1/attachment-rules/test-regex")
                        .param("regex", regex)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(filenames)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.error").exists());

        verify(attachmentRuleService, times(1)).testRegex(regex, filenames);
    }
}
