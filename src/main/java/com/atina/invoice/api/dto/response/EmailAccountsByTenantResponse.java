package com.atina.invoice.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO para cuentas de email agrupadas por tenant
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailAccountsByTenantResponse {

    @JsonProperty("tenantId")
    private Long tenantId;

    @JsonProperty("tenantCode")
    private String tenantCode;

    @JsonProperty("tenantName")
    private String tenantName;

    @JsonProperty("totalAccounts")
    private Integer totalAccounts;

    @JsonProperty("accounts")
    private List<EmailAccountResponse> accounts;
}
