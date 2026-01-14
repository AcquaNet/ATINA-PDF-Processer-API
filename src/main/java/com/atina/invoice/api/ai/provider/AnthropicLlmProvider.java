package com.atina.invoice.api.ai.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Anthropic (Claude) LLM Provider Implementation
 * Uses Spring AI Anthropic integration
 */
@Slf4j
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "anthropic")
@RequiredArgsConstructor
public class AnthropicLlmProvider implements LlmProvider {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    @Value("${ai.anthropic.model:claude-3-sonnet-20240229}")
    private String model;

    @Override
    public String generateTemplate(String prompt, Map<String, Object> context) {
        log.info("Generating template using Anthropic Claude ({})", model);

        try {
            // Build user message
            String userMessage = buildUserMessage(context);
            
            log.debug("System prompt length: {} chars", prompt.length());
            log.debug("User message length: {} chars", userMessage.length());

            // Call Anthropic via Spring AI
            ChatClient chatClient = ChatClient.builder(chatModel).build();
            
            String response = chatClient.prompt()
                    .system(prompt)
                    .user(userMessage)
                    .call()
                    .content();

            log.debug("Anthropic response length: {} chars", response.length());
            
            // Clean response
            response = cleanJsonResponse(response);
            
            // Validate JSON
            objectMapper.readTree(response);
            
            log.info("Template generated successfully using Anthropic");
            
            return response;

        } catch (Exception e) {
            log.error("Failed to generate template with Anthropic", e);
            throw new RuntimeException("Anthropic template generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "anthropic";
    }

    @Override
    public boolean isAvailable() {
        try {
            return chatModel != null;
        } catch (Exception e) {
            log.warn("Anthropic provider not available", e);
            return false;
        }
    }

    @Override
    public String getModelName() {
        return model;
    }

    private String buildUserMessage(Map<String, Object> context) throws JsonProcessingException {
        StringBuilder message = new StringBuilder();

        if (context.containsKey("documentDescription")) {
            message.append("Document Type: ").append(context.get("documentDescription")).append("\n\n");
        }

        if (context.containsKey("fieldHints")) {
            message.append("Desired Fields: ").append(context.get("fieldHints")).append("\n\n");
        }

        if (context.containsKey("doclingJsonSamples")) {
            message.append("Document Samples (Docling JSON):\n");
            message.append(objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(context.get("doclingJsonSamples")));
        }

        return message.toString();
    }

    private String cleanJsonResponse(String response) {
        if (response == null) {
            return null;
        }

        response = response.trim();
        if (response.startsWith("```json")) {
            response = response.substring(7);
        } else if (response.startsWith("```")) {
            response = response.substring(3);
        }
        
        if (response.endsWith("```")) {
            response = response.substring(0, response.length() - 3);
        }

        return response.trim();
    }
}
