package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.request.CreateEmailSenderRuleRequest;
import com.atina.invoice.api.dto.request.ImportSenderConfigRequest;
import com.atina.invoice.api.dto.request.UpdateEmailSenderRuleRequest;
import com.atina.invoice.api.dto.response.AttachmentProcessingRuleResponse;
import com.atina.invoice.api.dto.response.EmailSenderRuleResponse;
import com.atina.invoice.api.security.JwtTokenProvider;
import com.atina.invoice.api.security.TenantInterceptor;
import com.atina.invoice.api.service.EmailSenderRuleService;
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
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmailSenderRuleController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Email Sender Rule Controller Tests")
class EmailSenderRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmailSenderRuleService senderRuleService;

    // Mock de beans de seguridad
    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private TenantInterceptor tenantInterceptor;

    private EmailSenderRuleResponse sampleResponse;
    private CreateEmailSenderRuleRequest createRequest;
    private ImportSenderConfigRequest importRequest;

    @BeforeEach
    void setUp() {
        // Sample attachment rule response
        AttachmentProcessingRuleResponse attachmentRule = AttachmentProcessingRuleResponse.builder()
                .id(1L)
                .senderRuleId(1L)
                .ruleOrder(1)
                .fileNameRegex("^Invoice+([0-9])+(.PDF|.pdf)$")
                .source("invoice")
                .destination("jde")
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Sample response
        sampleResponse = EmailSenderRuleResponse.builder()
                .id(1L)
                .tenantId(1L)
                .tenantCode("ACME")
                .emailAccountId(1L)
                .emailAccountAddress("test@example.com")
                .senderEmail("sender@example.com")
                .senderId("12345")
                .senderName("Test Sender")
                .templateEmailReceived("reply-mail-received.html")
                .templateEmailProcessed("reply-mail-processed.html")
                .autoReplyEnabled(true)
                .processEnabled(true)
                .enabled(true)
                .attachmentRules(Arrays.asList(attachmentRule))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Create request
        createRequest = CreateEmailSenderRuleRequest.builder()
                .emailAccountId(1L)
                .senderEmail("sender@example.com")
                .senderId("12345")
                .senderName("Test Sender")
                .templateEmailReceived("reply-mail-received.html")
                .templateEmailProcessed("reply-mail-processed.html")
                .autoReplyEnabled(true)
                .processEnabled(true)
                .enabled(true)
                .build();

        // Import request
        ImportSenderConfigRequest.Templates templates = new ImportSenderConfigRequest.Templates();
        templates.setEmailReceived("reply-mail-received.html");
        templates.setEmailProcessed("reply-mail-processed.html");

        ImportSenderConfigRequest.Rule rule1 = new ImportSenderConfigRequest.Rule();
        rule1.setId(1);
        rule1.setFileRule("^Invoice+([0-9])+(.PDF|.pdf)$");
        rule1.setSource("invoice");
        rule1.setDestination("jde");
        rule1.setMetodo("");

        ImportSenderConfigRequest.Rule rule2 = new ImportSenderConfigRequest.Rule();
        rule2.setId(2);
        rule2.setFileRule("^30716412527_.*\\.(?i:pdf)$");
        rule2.setSource("atina");
        rule2.setDestination("jde");
        rule2.setMetodo("");

        importRequest = ImportSenderConfigRequest.builder()
                .email("sender@example.com")
                .id("12345")
                .templates(templates)
                .rules(Arrays.asList(rule1, rule2))
                .build();
    }

    // ========== GET /api/v1/sender-rules ==========

    @Test
    @DisplayName("GET /sender-rules - Should return all rules")
    void getAllRules_AsAdmin_ShouldReturnRules() throws Exception {
        // Given
        List<EmailSenderRuleResponse> rules = Arrays.asList(sampleResponse);
        when(senderRuleService.getAllRules()).thenReturn(rules);

        // When & Then
        mockMvc.perform(get("/api/v1/sender-rules")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].senderEmail").value("sender@example.com"))
                .andExpect(jsonPath("$.data[0].attachmentRules").isArray())
                .andExpect(jsonPath("$.data[0].attachmentRules[0].fileNameRegex").exists());

        verify(senderRuleService, times(1)).getAllRules();
    }

    // ========== GET /api/v1/sender-rules/{id} ==========

    @Test
    @DisplayName("GET /sender-rules/{id} - Should return rule by ID with attachment rules")
    void getRuleById_ValidId_ShouldReturnRule() throws Exception {
        // Given
        when(senderRuleService.getRuleById(1L)).thenReturn(sampleResponse);

        // When & Then
        mockMvc.perform(get("/api/v1/sender-rules/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.senderEmail").value("sender@example.com"))
                .andExpect(jsonPath("$.data.senderId").value("12345"))
                .andExpect(jsonPath("$.data.attachmentRules").isArray())
                .andExpect(jsonPath("$.data.attachmentRules[0].ruleOrder").value(1));

        verify(senderRuleService, times(1)).getRuleById(1L);
    }

    // ========== POST /api/v1/sender-rules ==========

    @Test
    @DisplayName("POST /sender-rules - Should create rule successfully")
    void createRule_ValidRequest_ShouldCreateRule() throws Exception {
        // Given
        when(senderRuleService.createRule(any(CreateEmailSenderRuleRequest.class)))
                .thenReturn(sampleResponse);

        // When & Then
        mockMvc.perform(post("/api/v1/sender-rules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.senderEmail").value("sender@example.com"));

        verify(senderRuleService, times(1)).createRule(any(CreateEmailSenderRuleRequest.class));
    }

    @Test
    @DisplayName("POST /sender-rules - Should return 400 for invalid sender email")
    void createRule_InvalidEmail_ShouldReturnBadRequest() throws Exception {
        // Given
        createRequest.setSenderEmail("invalid-email");

        // When & Then
        mockMvc.perform(post("/api/v1/sender-rules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isBadRequest());

        verify(senderRuleService, never()).createRule(any());
    }

    @Test
    @DisplayName("POST /sender-rules - Should return 400 for missing required fields")
    void createRule_MissingFields_ShouldReturnBadRequest() throws Exception {
        // Given
        createRequest.setSenderEmail(null);
        createRequest.setSenderId(null);

        // When & Then
        mockMvc.perform(post("/api/v1/sender-rules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isBadRequest());

        verify(senderRuleService, never()).createRule(any());
    }

    // ========== PUT /api/v1/sender-rules/{id} ==========

    @Test
    @DisplayName("PUT /sender-rules/{id} - Should update rule successfully")
    void updateRule_ValidRequest_ShouldUpdateRule() throws Exception {
        // Given
        UpdateEmailSenderRuleRequest updateRequest = UpdateEmailSenderRuleRequest.builder()
                .autoReplyEnabled(false)
                .processEnabled(false)
                .build();

        EmailSenderRuleResponse updated = EmailSenderRuleResponse.builder()
                .id(1L)
                .senderEmail("sender@example.com")
                .autoReplyEnabled(false)
                .processEnabled(false)
                .build();

        when(senderRuleService.updateRule(eq(1L), any(UpdateEmailSenderRuleRequest.class)))
                .thenReturn(updated);

        // When & Then
        mockMvc.perform(put("/api/v1/sender-rules/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.autoReplyEnabled").value(false))
                .andExpect(jsonPath("$.data.processEnabled").value(false));

        verify(senderRuleService, times(1)).updateRule(eq(1L), any(UpdateEmailSenderRuleRequest.class));
    }

    // ========== DELETE /api/v1/sender-rules/{id} ==========

    @Test
    @DisplayName("DELETE /sender-rules/{id} - Should delete rule as SYSTEM_ADMIN")
    void deleteRule_AsSystemAdmin_ShouldDeleteRule() throws Exception {
        // Given
        doNothing().when(senderRuleService).deleteRule(1L);

        // When & Then
        mockMvc.perform(delete("/api/v1/sender-rules/1")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(senderRuleService, times(1)).deleteRule(1L);
    }

    @Test
    @DisplayName("DELETE /sender-rules/{id} - Should return 403 for ADMIN")
    void deleteRule_AsAdmin_ShouldReturnForbidden() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/sender-rules/1")
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(senderRuleService, never()).deleteRule(any());
    }

    // ========== POST /api/v1/sender-rules/import-json ==========

    @Test
    @DisplayName("POST /sender-rules/import-json - Should import configuration successfully")
    void importFromJson_ValidConfig_ShouldImportSuccessfully() throws Exception {
        // Given
        when(senderRuleService.importFromJson(eq(1L), any(ImportSenderConfigRequest.class)))
                .thenReturn(sampleResponse);

        // When & Then
        mockMvc.perform(post("/api/v1/sender-rules/import-json")
                        .with(csrf())
                        .param("emailAccountId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(importRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.senderEmail").value("sender@example.com"))
                .andExpect(jsonPath("$.data.senderId").value("12345"))
                .andExpect(jsonPath("$.data.attachmentRules").isArray());

        verify(senderRuleService, times(1)).importFromJson(eq(1L), any(ImportSenderConfigRequest.class));
    }

    @Test
    @DisplayName("POST /sender-rules/import-json - Should return 400 for invalid JSON")
    void importFromJson_InvalidJson_ShouldReturnBadRequest() throws Exception {
        // Given
        importRequest.setEmail("invalid-email");

        // When & Then
        mockMvc.perform(post("/api/v1/sender-rules/import-json")
                        .with(csrf())
                        .param("emailAccountId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(importRequest)))
                .andExpect(status().isBadRequest());

        verify(senderRuleService, never()).importFromJson(any(), any());
    }

    @Test
    @DisplayName("POST /sender-rules/import-json - Should return 400 for empty rules")
    void importFromJson_EmptyRules_ShouldReturnBadRequest() throws Exception {
        // Given
        importRequest.setRules(Arrays.asList());

        // When & Then
        mockMvc.perform(post("/api/v1/sender-rules/import-json")
                        .with(csrf())
                        .param("emailAccountId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(importRequest)))
                .andExpect(status().isBadRequest());

        verify(senderRuleService, never()).importFromJson(any(), any());
    }

    @Test
    @DisplayName("POST /sender-rules/import-json - Should create sender rule with multiple attachment rules")
    void importFromJson_MultipleRules_ShouldImportAll() throws Exception {
        // Given
        AttachmentProcessingRuleResponse rule1 = AttachmentProcessingRuleResponse.builder()
                .id(1L)
                .ruleOrder(1)
                .fileNameRegex("^Invoice+([0-9])+(.PDF|.pdf)$")
                .source("invoice")
                .destination("jde")
                .build();

        AttachmentProcessingRuleResponse rule2 = AttachmentProcessingRuleResponse.builder()
                .id(2L)
                .ruleOrder(2)
                .fileNameRegex("^30716412527_.*\\.(?i:pdf)$")
                .source("atina")
                .destination("jde")
                .build();

        EmailSenderRuleResponse importedResponse = EmailSenderRuleResponse.builder()
                .id(1L)
                .senderEmail("sender@example.com")
                .senderId("12345")
                .attachmentRules(Arrays.asList(rule1, rule2))
                .build();

        when(senderRuleService.importFromJson(eq(1L), any(ImportSenderConfigRequest.class)))
                .thenReturn(importedResponse);

        // When & Then
        mockMvc.perform(post("/api/v1/sender-rules/import-json")
                        .with(csrf())
                        .param("emailAccountId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(importRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.attachmentRules").isArray())
                .andExpect(jsonPath("$.data.attachmentRules[0].fileNameRegex").value("^Invoice+([0-9])+(.PDF|.pdf)$"))
                .andExpect(jsonPath("$.data.attachmentRules[1].fileNameRegex").value("^30716412527_.*\\.(?i:pdf)$"));

        verify(senderRuleService, times(1)).importFromJson(eq(1L), any(ImportSenderConfigRequest.class));
    }
}
