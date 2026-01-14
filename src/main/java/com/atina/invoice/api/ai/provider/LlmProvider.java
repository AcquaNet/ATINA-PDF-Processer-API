package com.atina.invoice.api.ai.provider;

import java.util.Map;

/**
 * LLM Provider Interface
 * Defines contract for different LLM implementations
 * Allows switching between OpenAI, Anthropic, Ollama, etc.
 */
public interface LlmProvider {

    /**
     * Generate template JSON from prompt and context
     * 
     * @param prompt System prompt with instructions
     * @param context Context data (Docling JSON, field hints, etc.)
     * @return Generated template JSON as string
     */
    String generateTemplate(String prompt, Map<String, Object> context);

    /**
     * Get provider name (openai, anthropic, ollama, etc.)
     */
    String getProviderName();

    /**
     * Check if provider is available and configured
     */
    boolean isAvailable();

    /**
     * Get model name being used
     */
    String getModelName();

    /**
     * Estimate token count for given text (optional)
     */
    default int estimateTokens(String text) {
        // Simple estimation: ~4 chars per token
        return text.length() / 4;
    }
}
