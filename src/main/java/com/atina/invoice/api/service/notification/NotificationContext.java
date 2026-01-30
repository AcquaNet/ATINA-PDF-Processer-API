package com.atina.invoice.api.service.notification;

import com.atina.invoice.api.model.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationContext {

    private ProcessedEmail email;
    private List<ExtractionTask> tasks;
    private List<ProcessedAttachment> attachments;
    private WebhookCallbackResponse callbackResponse;
    private Tenant tenant;
    private EmailSenderRule senderRule;
}
