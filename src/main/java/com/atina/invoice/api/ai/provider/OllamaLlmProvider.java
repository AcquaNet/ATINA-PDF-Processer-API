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
 * Ollama LLM Provider Implementation
 * Uses Spring AI Ollama integration for local/self-hosted models
 */
@Slf4j
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "ollama")
@RequiredArgsConstructor
public class OllamaLlmProvider implements LlmProvider {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    @Value("${ai.ollama.model:llama3}")
    private String model;

    @Value("${ai.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Override
    public String generateTemplate(String prompt, Map<String, Object> context) {
        log.info("Generating template using Ollama ({}) at {}", model, baseUrl);

        try {
            // Build user message
            String userMessage = buildUserMessage(context);
            
            log.debug("System prompt length: {} chars", prompt.length());
            log.debug("User message length: {} chars", userMessage.length());

            // Call Ollama via Spring AI
            ChatClient chatClient = ChatClient.builder(chatModel).build();
            
            String response = chatClient.prompt()
                    .system(prompt)
                    .user(userMessage)
                    .call()
                    .content();

            log.debug("Ollama response length: {} chars", response.length());
            
            // Clean response
            response = cleanJsonResponse(response);
            
            // Validate JSON
            objectMapper.readTree(response);
            
            log.info("Template generated successfully using Ollama");
            
            return response;

        } catch (Exception e) {
            log.error("Failed to generate template with Ollama", e);
            throw new RuntimeException("Ollama template generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "ollama";
    }

    @Override
    public boolean isAvailable() {
        try {
            return chatModel != null;
        } catch (Exception e) {
            log.warn("Ollama provider not available at {}", baseUrl, e);
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
