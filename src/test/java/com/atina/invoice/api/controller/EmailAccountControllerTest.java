package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.request.CreateEmailAccountRequest;
import com.atina.invoice.api.dto.request.UpdateEmailAccountRequest;
import com.atina.invoice.api.dto.response.EmailAccountResponse;
import com.atina.invoice.api.model.enums.EmailType;
import com.atina.invoice.api.security.JwtTokenProvider;
import com.atina.invoice.api.security.TenantInterceptor;
import com.atina.invoice.api.service.EmailAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests para EmailAccountController
 * Solución: @AutoConfigureMockMvc(addFilters = false) + @MockBean para seguridad
 */
@WebMvcTest(EmailAccountController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Email Account Controller Tests")
class EmailAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmailAccountService emailAccountService;

    // Mock de beans de seguridad
    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private TenantInterceptor tenantInterceptor;

    private EmailAccountResponse sampleResponse;
    private CreateEmailAccountRequest createRequest;
    private UpdateEmailAccountRequest updateRequest;

    @BeforeEach
    void setUp() {
        sampleResponse = EmailAccountResponse.builder()
                .id(1L)
                .tenantId(1L)
                .tenantCode("ACME")
                .emailAddress("test@example.com")
                .emailType(EmailType.IMAP)
                .host("imap.example.com")
                .port(993)
                .username("test@example.com")
                .useSsl(true)
                .pollingEnabled(true)
                .pollingIntervalMinutes(10)
                .folderName("INBOX")
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        createRequest = CreateEmailAccountRequest.builder()
                .emailAddress("test@example.com")
                .emailType(EmailType.IMAP)
                .host("imap.example.com")
                .port(993)
                .username("test@example.com")
                .password("password123")
                .useSsl(true)
                .pollingEnabled(true)
                .pollingIntervalMinutes(10)
                .folderName("INBOX")
                .enabled(true)
                .build();

        updateRequest = UpdateEmailAccountRequest.builder()
                .pollingIntervalMinutes(15)
                .enabled(false)
                .build();
    }

    @Test
    @DisplayName("GET /email-accounts - Should return all accounts")
    void getAllAccounts_ShouldReturnAccounts() throws Exception {
        List<EmailAccountResponse> accounts = Arrays.asList(sampleResponse);
        when(emailAccountService.getAllAccounts()).thenReturn(accounts);

        mockMvc.perform(get("/api/v1/email-accounts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].emailAddress").value("test@example.com"));

        verify(emailAccountService, times(1)).getAllAccounts();
    }

    @Test
    @DisplayName("GET /email-accounts/{id} - Should return account by ID")
    void getAccountById_ValidId_ShouldReturnAccount() throws Exception {
        when(emailAccountService.getAccountById(1L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/email-accounts/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));

        verify(emailAccountService, times(1)).getAccountById(1L);
    }

    @Test
    @DisplayName("POST /email-accounts - Should create account successfully")
    void createAccount_ValidRequest_ShouldCreateAccount() throws Exception {
        when(emailAccountService.createAccount(any(CreateEmailAccountRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/email-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.emailAddress").value("test@example.com"));

        verify(emailAccountService, times(1)).createAccount(any(CreateEmailAccountRequest.class));
    }

    @Test
    @DisplayName("POST /email-accounts - Should return 400 for invalid email")
    void createAccount_InvalidEmail_ShouldReturnBadRequest() throws Exception {
        createRequest.setEmailAddress("invalid-email");

        mockMvc.perform(post("/api/v1/email-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isBadRequest());

        verify(emailAccountService, never()).createAccount(any());
    }

    @Test
    @DisplayName("PUT /email-accounts/{id} - Should update account")
    void updateAccount_ValidRequest_ShouldUpdateAccount() throws Exception {
        EmailAccountResponse updated = EmailAccountResponse.builder()
                .id(1L)
                .pollingIntervalMinutes(15)
                .enabled(false)
                .build();

        when(emailAccountService.updateAccount(eq(1L), any(UpdateEmailAccountRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/email-accounts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(emailAccountService, times(1)).updateAccount(eq(1L), any(UpdateEmailAccountRequest.class));
    }

    @Test
    @DisplayName("DELETE /email-accounts/{id} - Should delete account")
    void deleteAccount_ValidId_ShouldDeleteAccount() throws Exception {
        doNothing().when(emailAccountService).deleteAccount(1L);

        mockMvc.perform(delete("/api/v1/email-accounts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(emailAccountService, times(1)).deleteAccount(1L);
    }

    @Test
    @DisplayName("PATCH /email-accounts/{id}/toggle-polling - Should toggle polling")
    void togglePolling_ShouldTogglePolling() throws Exception {
        EmailAccountResponse toggled = EmailAccountResponse.builder()
                .id(1L)
                .pollingEnabled(false)
                .build();

        when(emailAccountService.togglePolling(1L, false)).thenReturn(toggled);

        mockMvc.perform(patch("/api/v1/email-accounts/1/toggle-polling")
                        .param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(emailAccountService, times(1)).togglePolling(1L, false);
    }

    @Test
    @DisplayName("POST /email-accounts/{id}/test-connection - Should test connection")
    void testConnection_ShouldReturnSuccess() throws Exception {
        String result = "Connection successful! Found 42 messages in folder 'INBOX'";
        when(emailAccountService.testConnection(1L)).thenReturn(result);

        mockMvc.perform(post("/api/v1/email-accounts/1/test-connection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(result));

        verify(emailAccountService, times(1)).testConnection(1L);
    }
}
