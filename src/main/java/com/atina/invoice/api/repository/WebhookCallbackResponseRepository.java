package com.atina.invoice.api.repository;

import com.atina.invoice.api.model.WebhookCallbackResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookCallbackResponseRepository extends JpaRepository<WebhookCallbackResponse, Long> {

    List<WebhookCallbackResponse> findByCorrelationId(String correlationId);
}
