package com.atina.invoice.api.service;

import com.atina.invoice.api.dto.request.CreateEmailAccountRequest;
import com.atina.invoice.api.dto.request.UpdateEmailAccountRequest;
import com.atina.invoice.api.dto.response.EmailAccountResponse;
import com.atina.invoice.api.mapper.EmailAccountMapper;
import com.atina.invoice.api.model.EmailAccount;
import com.atina.invoice.api.model.Tenant;
import com.atina.invoice.api.model.enums.EmailType;
import com.atina.invoice.api.repository.EmailAccountRepository;
import com.atina.invoice.api.repository.TenantRepository;
import com.atina.invoice.api.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Email Account Service Tests")
class EmailAccountServiceTest {

    @Mock
    private EmailAccountRepository emailAccountRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private EmailAccountMapper emailAccountMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private EmailAccountService emailAccountService;

    private MockedStatic<TenantContext> tenantContextMock;

    private Tenant tenant;
    private EmailAccount emailAccount;
    private CreateEmailAccountRequest createRequest;
    private EmailAccountResponse emailAccountResponse;

    @BeforeEach
    void setUp() {
        // Mock TenantContext
        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::getTenantId).thenReturn(1L);

        // Setup tenant
        tenant = Tenant.builder()
                .id(1L)
                .tenantCode("ACME")
                .build();

        // Setup email account
        emailAccount = EmailAccount.builder()
                .id(1L)
                .tenant(tenant)
                .emailAddress("test@example.com")
                .emailType(EmailType.IMAP)
                .host("imap.example.com")
                .port(993)
                .username("test@example.com")
                .password("encrypted_password")
                .useSsl(true)
                .pollingEnabled(true)
                .pollingIntervalMinutes(10)
                .folderName("INBOX")
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Setup create request
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

        // Setup response
        emailAccountResponse = EmailAccountResponse.builder()
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
                .build();
    }

    @AfterEach
    void tearDown() {
        tenantContextMock.close();
    }

    // ========== getAllAccounts ==========

    @Test
    @DisplayName("getAllAccounts - Should return all accounts for current tenant")
    void getAllAccounts_ShouldReturnAccountsForTenant() {
        // Given
        List<EmailAccount> accounts = Arrays.asList(emailAccount);
        when(emailAccountRepository.findByTenantId(1L)).thenReturn(accounts);
        when(emailAccountMapper.toResponse(emailAccount)).thenReturn(emailAccountResponse);

        // When
        List<EmailAccountResponse> result = emailAccountService.getAllAccounts();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmailAddress()).isEqualTo("test@example.com");
        verify(emailAccountRepository, times(1)).findByTenantId(1L);
        verify(emailAccountMapper, times(1)).toResponse(emailAccount);
    }

    // ========== getAccountById ==========

    @Test
    @DisplayName("getAccountById - Should return account when found and belongs to tenant")
    void getAccountById_WhenFound_ShouldReturnAccount() {
        // Given
        when(emailAccountRepository.findById(1L)).thenReturn(Optional.of(emailAccount));
        when(emailAccountMapper.toResponse(emailAccount)).thenReturn(emailAccountResponse);

        // When
        EmailAccountResponse result = emailAccountService.getAccountById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmailAddress()).isEqualTo("test@example.com");
        verify(emailAccountRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("getAccountById - Should throw exception when account not found")
    void getAccountById_WhenNotFound_ShouldThrowException() {
        // Given
        when(emailAccountRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> emailAccountService.getAccountById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email account not found: 999");

        verify(emailAccountRepository, times(1)).findById(999L);
        verify(emailAccountMapper, never()).toResponse(any());
    }

    @Test
    @DisplayName("getAccountById - Should throw exception when account belongs to different tenant")
    void getAccountById_WhenDifferentTenant_ShouldThrowException() {
        // Given
        Tenant differentTenant = Tenant.builder().id(2L).build();
        emailAccount.setTenant(differentTenant);
        when(emailAccountRepository.findById(1L)).thenReturn(Optional.of(emailAccount));

        // When & Then
        assertThatThrownBy(() -> emailAccountService.getAccountById(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("does not belong to current tenant");

        verify(emailAccountRepository, times(1)).findById(1L);
    }

    // ========== createAccount ==========

    @Test
    @DisplayName("createAccount - Should create account successfully")
    void createAccount_ValidRequest_ShouldCreateAccount() {
        // Given
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(emailAccountRepository.existsByTenantIdAndEmailAddress(1L, "test@example.com"))
                .thenReturn(false);
        when(emailAccountMapper.toEntity(createRequest, tenant)).thenReturn(emailAccount);
        when(passwordEncoder.encode("password123")).thenReturn("encrypted_password");
        when(emailAccountRepository.save(any(EmailAccount.class))).thenReturn(emailAccount);
        when(emailAccountMapper.toResponse(emailAccount)).thenReturn(emailAccountResponse);

        // When
        EmailAccountResponse result = emailAccountService.createAccount(createRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEmailAddress()).isEqualTo("test@example.com");
        verify(tenantRepository, times(1)).findById(1L);
        verify(emailAccountRepository, times(1)).existsByTenantIdAndEmailAddress(1L, "test@example.com");
        verify(passwordEncoder, times(1)).encode("password123");
        verify(emailAccountRepository, times(1)).save(any(EmailAccount.class));
    }

    @Test
    @DisplayName("createAccount - Should throw exception when email already exists")
    void createAccount_WhenEmailExists_ShouldThrowException() {
        // Given
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(emailAccountRepository.existsByTenantIdAndEmailAddress(1L, "test@example.com"))
                .thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> emailAccountService.createAccount(createRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email account already exists");

        verify(emailAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("createAccount - Should throw exception when tenant not found")
    void createAccount_WhenTenantNotFound_ShouldThrowException() {
        // Given
        when(tenantRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> emailAccountService.createAccount(createRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Tenant not found: 1");

        verify(emailAccountRepository, never()).save(any());
    }

    // ========== updateAccount ==========

    @Test
    @DisplayName("updateAccount - Should update account successfully")
    void updateAccount_ValidRequest_ShouldUpdateAccount() {
        // Given
        UpdateEmailAccountRequest updateRequest = UpdateEmailAccountRequest.builder()
                .pollingIntervalMinutes(15)
                .enabled(false)
                .build();

        when(emailAccountRepository.findById(1L)).thenReturn(Optional.of(emailAccount));
        doNothing().when(emailAccountMapper).updateEntity(emailAccount, updateRequest);
        when(emailAccountRepository.save(emailAccount)).thenReturn(emailAccount);
        when(emailAccountMapper.toResponse(emailAccount)).thenReturn(emailAccountResponse);

        // When
        EmailAccountResponse result = emailAccountService.updateAccount(1L, updateRequest);

        // Then
        assertThat(result).isNotNull();
        verify(emailAccountMapper, times(1)).updateEntity(emailAccount, updateRequest);
        verify(emailAccountRepository, times(1)).save(emailAccount);
    }

    @Test
    @DisplayName("updateAccount - Should encrypt new password when updating")
    void updateAccount_WithNewPassword_ShouldEncryptPassword() {
        // Given
        UpdateEmailAccountRequest updateRequest = UpdateEmailAccountRequest.builder()
                .password("newpassword123")
                .build();

        when(emailAccountRepository.findById(1L)).thenReturn(Optional.of(emailAccount));
        when(passwordEncoder.encode("newpassword123")).thenReturn("new_encrypted_password");
        when(emailAccountRepository.save(emailAccount)).thenReturn(emailAccount);
        when(emailAccountMapper.toResponse(emailAccount)).thenReturn(emailAccountResponse);

        // When
        emailAccountService.updateAccount(1L, updateRequest);

        // Then
        verify(passwordEncoder, times(1)).encode("newpassword123");
        verify(emailAccountRepository, times(1)).save(emailAccount);
    }

    @Test
    @DisplayName("updateAccount - Should throw exception when trying to use existing email")
    void updateAccount_WithExistingEmail_ShouldThrowException() {
        // Given
        UpdateEmailAccountRequest updateRequest = UpdateEmailAccountRequest.builder()
                .emailAddress("another@example.com")
                .build();

        when(emailAccountRepository.findById(1L)).thenReturn(Optional.of(emailAccount));
        when(emailAccountRepository.existsByTenantIdAndEmailAddress(1L, "another@example.com"))
                .thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> emailAccountService.updateAccount(1L, updateRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email account already exists");

        verify(emailAccountRepository, never()).save(any());
    }

    // ========== deleteAccount ==========

    @Test
    @DisplayName("deleteAccount - Should delete account successfully")
    void deleteAccount_ValidId_ShouldDeleteAccount() {
        // Given
        when(emailAccountRepository.findById(1L)).thenReturn(Optional.of(emailAccount));
        doNothing().when(emailAccountRepository).delete(emailAccount);

        // When
        emailAccountService.deleteAccount(1L);

        // Then
        verify(emailAccountRepository, times(1)).delete(emailAccount);
    }

    // ========== togglePolling ==========

    @Test
    @DisplayName("togglePolling - Should toggle polling successfully")
    void togglePolling_ShouldTogglePolling() {
        // Given
        when(emailAccountRepository.findById(1L)).thenReturn(Optional.of(emailAccount));
        when(emailAccountRepository.save(emailAccount)).thenReturn(emailAccount);
        when(emailAccountMapper.toResponse(emailAccount)).thenReturn(emailAccountResponse);

        // When
        EmailAccountResponse result = emailAccountService.togglePolling(1L, false);

        // Then
        assertThat(result).isNotNull();
        assertThat(emailAccount.getPollingEnabled()).isFalse();
        verify(emailAccountRepository, times(1)).save(emailAccount);
    }
}
