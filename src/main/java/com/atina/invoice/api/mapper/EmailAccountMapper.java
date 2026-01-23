package com.atina.invoice.api.mapper;

import com.atina.invoice.api.dto.request.CreateEmailAccountRequest;
import com.atina.invoice.api.dto.request.UpdateEmailAccountRequest;
import com.atina.invoice.api.dto.response.EmailAccountResponse;
import com.atina.invoice.api.model.EmailAccount;
import com.atina.invoice.api.model.Tenant;
import org.springframework.stereotype.Component;

/**
 * Mapper para EmailAccount
 */
@Component
public class EmailAccountMapper {

    /**
     * Convertir CreateRequest a Entity
     */
    public EmailAccount toEntity(CreateEmailAccountRequest request, Tenant tenant) {
        return EmailAccount.builder()
                .tenant(tenant)
                .emailAddress(request.getEmailAddress())
                .emailType(request.getEmailType())
                .host(request.getHost())
                .port(request.getPort())
                .username(request.getUsername())
                .password(request.getPassword()) // Se encriptará en el service
                .useSsl(request.getUseSsl())
                .pollingEnabled(request.getPollingEnabled())
                .pollingIntervalMinutes(request.getPollingIntervalMinutes())
                .folderName(request.getFolderName())
                .description(request.getDescription())
                .enabled(request.getEnabled())
                .build();
    }

    /**
     * Actualizar entity desde UpdateRequest
     */
    public void updateEntity(EmailAccount entity, UpdateEmailAccountRequest request) {
        if (request.getEmailAddress() != null) {
            entity.setEmailAddress(request.getEmailAddress());
        }
        if (request.getEmailType() != null) {
            entity.setEmailType(request.getEmailType());
        }
        if (request.getHost() != null) {
            entity.setHost(request.getHost());
        }
        if (request.getPort() != null) {
            entity.setPort(request.getPort());
        }
        if (request.getUsername() != null) {
            entity.setUsername(request.getUsername());
        }
        if (request.getPassword() != null) {
            entity.setPassword(request.getPassword()); // Se encriptará en el service
        }
        if (request.getUseSsl() != null) {
            entity.setUseSsl(request.getUseSsl());
        }
        if (request.getPollingEnabled() != null) {
            entity.setPollingEnabled(request.getPollingEnabled());
        }
        if (request.getPollingIntervalMinutes() != null) {
            entity.setPollingIntervalMinutes(request.getPollingIntervalMinutes());
        }
        if (request.getFolderName() != null) {
            entity.setFolderName(request.getFolderName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        }
    }

    /**
     * Convertir Entity a Response
     */
    public EmailAccountResponse toResponse(EmailAccount entity) {
        return EmailAccountResponse.builder()
                .id(entity.getId())
                .tenantId(entity.getTenant().getId())
                .tenantCode(entity.getTenant().getTenantCode())
                .emailAddress(entity.getEmailAddress())
                .emailType(entity.getEmailType())
                .host(entity.getHost())
                .port(entity.getPort())
                .username(entity.getUsername())
                // password NO se devuelve por seguridad
                .useSsl(entity.getUseSsl())
                .pollingEnabled(entity.getPollingEnabled())
                .pollingIntervalMinutes(entity.getPollingIntervalMinutes())
                .folderName(entity.getFolderName())
                .lastPollDate(entity.getLastPollDate())
                .lastSuccessfulPoll(entity.getLastSuccessfulPoll())
                .lastProcessedUid(entity.getLastProcessedUid())
                .description(entity.getDescription())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
