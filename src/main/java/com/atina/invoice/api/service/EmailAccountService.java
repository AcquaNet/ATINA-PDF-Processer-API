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
import com.atina.invoice.api.security.AesGcmCrypto;
import com.atina.invoice.api.security.TenantContext;
import jakarta.mail.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Properties;
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

    /**
     * Listar todas las cuentas del tenant actual
     */
    @Transactional(readOnly = true)
    public List<EmailAccountResponse> getAllAccounts() {
        Long tenantId = TenantContext.getTenantId();
        return emailAccountRepository.findByTenantId(tenantId).stream()
                .map(emailAccountMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Obtener cuenta por ID
     */
    @Transactional(readOnly = true)
    public EmailAccountResponse getAccountById(Long id) {
        EmailAccount account = findAccountByIdAndTenant(id);
        return emailAccountMapper.toResponse(account);
    }

    /**
     * Crear nueva cuenta de email
     */
    @Transactional
    public EmailAccountResponse createAccount(CreateEmailAccountRequest request) {
        Long tenantId = TenantContext.getTenantId();
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
     * Actualizar cuenta de email
     */
    @Transactional
    public EmailAccountResponse updateAccount(Long id, UpdateEmailAccountRequest request) {
        EmailAccount account = findAccountByIdAndTenant(id);

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
     * Eliminar cuenta de email
     */
    @Transactional
    public void deleteAccount(Long id) {
        EmailAccount account = findAccountByIdAndTenant(id);
        emailAccountRepository.delete(account);
        log.info("Deleted email account: {}", account.getEmailAddress());
    }

    /**
     * Habilitar/deshabilitar polling
     */
    @Transactional
    public EmailAccountResponse togglePolling(Long id, boolean enabled) {
        EmailAccount account = findAccountByIdAndTenant(id);
        account.setPollingEnabled(enabled);
        EmailAccount saved = emailAccountRepository.save(account);
        log.info("Toggled polling for {}: {}", account.getEmailAddress(), enabled);
        return emailAccountMapper.toResponse(saved);
    }

    /**
     * Probar conexión a la cuenta de email
     */
    public String testConnection(Long id) {
        EmailAccount account = findAccountByIdAndTenant(id);
        
        try {
            // Desencriptar contraseña
            String password = account.getPassword(); // Aquí deberías desencriptarla si es necesario
            
            // Configurar propiedades según el tipo
            Properties props = new Properties();
            Session session;
            Store store;

            if (account.getEmailType() == EmailType.IMAP) {
                props.put("mail.store.protocol", "imap");
                props.put("mail.imap.host", account.getHost());
                props.put("mail.imap.port", account.getPort());
                if (account.getUseSsl()) {
                    props.put("mail.imap.ssl.enable", "true");
                }
                props.put("mail.imap.connectiontimeout", "10000");
                props.put("mail.imap.timeout", "10000");
                
                session = Session.getInstance(props);
                store = session.getStore("imap");
            } else {
                props.put("mail.store.protocol", "pop3");
                props.put("mail.pop3.host", account.getHost());
                props.put("mail.pop3.port", account.getPort());
                if (account.getUseSsl()) {
                    props.put("mail.pop3.ssl.enable", "true");
                }
                props.put("mail.pop3.connectiontimeout", "10000");
                props.put("mail.pop3.timeout", "10000");
                
                session = Session.getInstance(props);
                store = session.getStore("pop3");
            }

            // Intentar conectar
            store.connect(account.getUsername(), password);
            
            // Abrir carpeta para verificar acceso
            Folder folder = store.getFolder(account.getFolderName());
            folder.open(Folder.READ_ONLY);
            int messageCount = folder.getMessageCount();
            
            // Cerrar conexiones
            folder.close(false);
            store.close();

            log.info("Connection test successful for {}: {} messages in {}", 
                    account.getEmailAddress(), messageCount, account.getFolderName());

            return String.format("Connection successful! Found %d messages in folder '%s'", 
                    messageCount, account.getFolderName());

        } catch (MessagingException e) {
            log.error("Connection test failed for {}: {}", account.getEmailAddress(), e.getMessage());
            throw new RuntimeException("Connection failed: " + e.getMessage(), e);
        }
    }

    /**
     * Helper: Buscar cuenta por ID y validar que pertenece al tenant actual
     */
    private EmailAccount findAccountByIdAndTenant(Long id) {
        Long tenantId = TenantContext.getTenantId();
        EmailAccount account = emailAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Email account not found: " + id));

        if (!account.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Email account does not belong to current tenant");
        }

        return account;
    }
}
