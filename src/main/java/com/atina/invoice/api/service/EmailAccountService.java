package com.atina.invoice.api.service;

import com.atina.invoice.api.dto.request.CreateEmailAccountRequest;
import com.atina.invoice.api.dto.request.UpdateEmailAccountRequest;
import com.atina.invoice.api.dto.response.EmailAccountResponse;
import com.atina.invoice.api.dto.response.EmailAccountsByTenantResponse;
import com.atina.invoice.api.mapper.EmailAccountMapper;
import com.atina.invoice.api.model.EmailAccount;
import com.atina.invoice.api.model.Tenant;
import com.atina.invoice.api.model.enums.EmailType;
import com.atina.invoice.api.repository.EmailAccountRepository;
import com.atina.invoice.api.repository.TenantRepository;
import com.atina.invoice.api.security.AesGcmCrypto;
import jakarta.mail.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service para gestión de cuentas de email
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAccountService {

    private final EmailAccountRepository emailAccountRepository;
    private final TenantRepository tenantRepository;
    private final EmailAccountMapper emailAccountMapper;
    private final AesGcmCrypto aesGcmCrypto;
    private final EmailReaderService emailReaderService;

    /**
     * Listar todas las cuentas de todos los tenants (solo para SYSTEM_ADMIN)
     */
    @Transactional(readOnly = true)
    public List<EmailAccountResponse> getAllAccounts() {
        return emailAccountRepository.findAll().stream()
                .map(emailAccountMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Obtener cuenta por ID (sin validar tenant - solo para SYSTEM_ADMIN)
     */
    @Transactional(readOnly = true)
    public EmailAccountResponse getAccountById(Long id) {
        EmailAccount account = emailAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Email account not found: " + id));
        return emailAccountMapper.toResponse(account);
    }

    /**
     * Crear nueva cuenta de email
     */
    @Transactional
    public EmailAccountResponse createAccount(CreateEmailAccountRequest request) {
        Long tenantId = request.getTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));

        // Validar que no exista una cuenta con ese email en el tenant
        if (emailAccountRepository.existsByTenantIdAndEmailAddress(tenantId, request.getEmailAddress())) {
            throw new RuntimeException("Email account already exists: " + request.getEmailAddress());
        }

        // Crear entidad
        EmailAccount account = emailAccountMapper.toEntity(request, tenant);
        
        // Encriptar contraseña
        account.setPassword(aesGcmCrypto.encryptToBase64(request.getPassword()));

        // Guardar
        EmailAccount saved = emailAccountRepository.save(account);
        log.info("Created email account: {} for tenant: {}", saved.getEmailAddress(), tenant.getTenantCode());

        return emailAccountMapper.toResponse(saved);
    }

    /**
     * Actualizar cuenta de email (sin validar tenant - solo para SYSTEM_ADMIN)
     */
    @Transactional
    public EmailAccountResponse updateAccount(Long id, UpdateEmailAccountRequest request) {
        EmailAccount account = emailAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Email account not found: " + id));

        // Si se actualiza el email, validar que no exista
        if (request.getEmailAddress() != null &&
            !request.getEmailAddress().equals(account.getEmailAddress())) {
            if (emailAccountRepository.existsByTenantIdAndEmailAddress(
                    account.getTenant().getId(), request.getEmailAddress())) {
                throw new RuntimeException("Email account already exists: " + request.getEmailAddress());
            }
        }

        // Actualizar campos
        emailAccountMapper.updateEntity(account, request);

        // Si se actualiza la contraseña, encriptarla
        if (request.getPassword() != null) {
            account.setPassword(aesGcmCrypto.encryptToBase64(request.getPassword()));
        }

        // Guardar
        EmailAccount saved = emailAccountRepository.save(account);
        log.info("Updated email account: {}", saved.getEmailAddress());

        return emailAccountMapper.toResponse(saved);
    }

    /**
     * Eliminar cuenta de email (sin validar tenant - solo para SYSTEM_ADMIN)
     */
    @Transactional
    public void deleteAccount(Long id) {
        EmailAccount account = emailAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Email account not found: " + id));
        emailAccountRepository.delete(account);
        log.info("Deleted email account: {}", account.getEmailAddress());
    }

    /**
     * Habilitar/deshabilitar polling (sin validar tenant - solo para SYSTEM_ADMIN)
     */
    @Transactional
    public EmailAccountResponse togglePolling(Long id, boolean enabled) {
        EmailAccount account = emailAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Email account not found: " + id));
        account.setPollingEnabled(enabled);
        EmailAccount saved = emailAccountRepository.save(account);
        log.info("Toggled polling for {}: {}", account.getEmailAddress(), enabled);
        return emailAccountMapper.toResponse(saved);
    }

    /**
     * Probar conexión a la cuenta de email (sin validar tenant - solo para SYSTEM_ADMIN)
     */
    public String testConnection(Long id) {

        // ----------------------------------------------------------------
        // Read Email Account
        // ----------------------------------------------------------------

        EmailAccount emailAccount = emailAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Email account not found: " + id));
        
        try {

            EmailReaderService.EmailReadContext context =
                    emailReaderService.openEmailFolder(emailAccount, false,true);

            int messageCount = context.getFolder().getMessageCount();

            log.info("Connection test successful for {}: {} messages in {}", 
                    emailAccount.getEmailAddress(), messageCount, emailAccount.getFolderName());

            return String.format("Connection successful! Found %d messages in folder '%s'", 
                    messageCount, emailAccount.getFolderName());

        } catch (MessagingException e) {
            log.error("Connection test failed for {}: {}", emailAccount.getEmailAddress(), e.getMessage());
            throw new RuntimeException("Connection failed: " + e.getMessage(), e);
        }
    }

    /**
     * Listar todas las cuentas agrupadas por tenant (solo para SYSTEM_ADMIN)
     */
    @Transactional(readOnly = true)
    public List<EmailAccountsByTenantResponse> getAccountsByTenant() {
        List<EmailAccount> allAccounts = emailAccountRepository.findAll();

        // Agrupar por tenant
        Map<Tenant, List<EmailAccount>> accountsByTenant = allAccounts.stream()
                .collect(Collectors.groupingBy(EmailAccount::getTenant));

        // Convertir a response
        return accountsByTenant.entrySet().stream()
                .map(entry -> {
                    Tenant tenant = entry.getKey();
                    List<EmailAccount> accounts = entry.getValue();

                    return EmailAccountsByTenantResponse.builder()
                            .tenantId(tenant.getId())
                            .tenantCode(tenant.getTenantCode())
                            .tenantName(tenant.getTenantName())
                            .totalAccounts(accounts.size())
                            .accounts(accounts.stream()
                                    .map(emailAccountMapper::toResponse)
                                    .collect(Collectors.toList()))
                            .build();
                })
                .sorted(Comparator.comparing(EmailAccountsByTenantResponse::getTenantCode))
                .collect(Collectors.toList());
    }

}
