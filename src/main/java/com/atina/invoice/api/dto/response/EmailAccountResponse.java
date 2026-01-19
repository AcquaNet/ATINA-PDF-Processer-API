package com.atina.invoice.api.dto.response;

import com.atina.invoice.api.model.enums.EmailType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response con datos de una cuenta de email
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailAccountResponse {

    private Long id;
    private Long tenantId;
    private String tenantCode;
    
    // Configuración de conexión
    private String emailAddress;
    private EmailType emailType;
    private String host;
    private Integer port;
    private String username;
    // password NO se devuelve por seguridad
    private Boolean useSsl;
    
    // Configuración de polling
    private Boolean pollingEnabled;
    private Integer pollingIntervalMinutes;
    private String folderName;
    
    // Metadata de polling
    private Instant lastPollDate;
    private Instant lastSuccessfulPoll;
    private String lastProcessedUid;
    
    // Metadata general
    private String description;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}
