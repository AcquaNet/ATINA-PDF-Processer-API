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
 * OpenAI LLM Provider Implementation
 * Uses Spring AI OpenAI integration
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "openai")
@RequiredArgsConstructor
public class OpenAILlmProvider implements LlmProvider {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    @Value("${ai.openai.model:gpt-4}")
    private String model;

    @Override
    public String generateTemplate(String prompt, Map<String, Object> context) {
        log.info("Generating template using OpenAI ({})", model);

        try {
            // Build user message with context
            String userMessage = buildUserMessage(context);

            log.info("System prompt length: {} chars", prompt.length());
            log.info("System prompt value: {} chars", prompt);
            log.info("User message length: {} chars", userMessage.length());
            log.info("User message value: {} chars", userMessage);

            log.info("=== DEBUGGING ===");
            log.info("System prompt length: {} chars", prompt.length());
            log.info("System prompt: {}", prompt.substring(0, Math.min(500, prompt.length())) + "...");
            log.info("User message length: {} chars", userMessage.length());
            log.info("User message: {}", userMessage.substring(0, Math.min(500, userMessage.length())) + "...");
            log.info("=================");

            log.info("=== REQUEST TO OPENAI ===");
            log.info("System prompt: {}", prompt.substring(0, Math.min(1000, prompt.length())));
            log.info("User message: {}", userMessage.substring(0, Math.min(1000, userMessage.length())));
            log.info("========================");


            // Call OpenAI via Spring AI
            ChatClient chatClient = ChatClient.builder(chatModel).build();

            // Get full response object
            var chatResponse = chatClient.prompt()
                    .system(prompt)
                    .user(userMessage)
                    .call()
                    .chatResponse();

            log.info("=== OPENAI FULL RESPONSE ===");
            log.info("Response object: {}", chatResponse);
            log.info("Results count: {}", chatResponse.getResults().size());

            if (!chatResponse.getResults().isEmpty()) {
                var result = chatResponse.getResults().get(0);
                log.info("Result output: {}", result.getOutput());
                log.info("Result content: {}", result.getOutput().getContent());
            }

            String response = chatResponse.getResult().getOutput().getContent();

            if (response == null || response.isEmpty()) {
                log.error("OpenAI returned empty response!");
                log.error("Metadata: {}", chatResponse.getMetadata());
                throw new RuntimeException("OpenAI returned empty response");
            }

            log.info("OpenAI response length: {} chars", response.length());
            log.info("OpenAI response preview: {}",
                    response.substring(0, Math.min(500, response.length())));
            // Clean response (remove markdown fences if present)
            response = cleanJsonResponse(response);

            // Validate it's valid JSON
            objectMapper.readTree(response);

            log.info("Template generated successfully using OpenAI");

            return response;

        } catch (Exception e) {
            log.error("Failed to generate template with OpenAI", e);
            throw new RuntimeException("OpenAI template generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "openai";
    }

    @Override
    public boolean isAvailable() {
        try {
            // Simple availability check
            return chatModel != null;
        } catch (Exception e) {
            log.warn("OpenAI provider not available", e);
            return false;
        }
    }

    @Override
    public String getModelName() {
        return model;
    }

    /**
     * Build user message from context
     */
    private String buildUserMessage(Map<String, Object> context) throws JsonProcessingException {
        StringBuilder message = new StringBuilder();

        // Document description
        if (context.containsKey("documentDescription")) {
            message.append("Document Type: ").append(context.get("documentDescription")).append("\n\n");
        }

        // Field hints
        if (context.containsKey("fieldHints")) {
            message.append("Desired Fields: ").append(context.get("fieldHints")).append("\n\n");
        }

        // Docling JSON samples
        if (context.containsKey("doclingJsonSamples")) {
            message.append("Document Samples (Docling JSON):\n");
            message.append(objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(context.get("doclingJsonSamples")));
        }

        return message.toString();
    }

    /**
     * Clean JSON response by removing markdown fences
     */
    private String cleanJsonResponse(String response) {
        if (response == null) {
            return null;
        }

        // Remove ```json and ``` fences
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
