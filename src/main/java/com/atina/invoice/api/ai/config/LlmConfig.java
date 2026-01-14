package com.atina.invoice.api.ai.config;

import com.atina.invoice.api.ai.provider.LlmProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * LLM Configuration
 * Selects and configures the appropriate LLM provider
 */
@Slf4j
@Configuration
public class LlmConfig {

    @Value("${OPENAI_API_KEY:NOT_SET}")
    private String openaiKey;

    @Value("${ai.provider:openai}")
    private String configuredProvider;

    /**
     * Select the active LLM provider
     * Spring will inject all available implementations
     */
    @Bean
    @Primary
    public LlmProvider activeLlmProvider(List<LlmProvider> availableProviders) {
        log.info("OPENAI_API_KEY value: {}",
                openaiKey.substring(0, Math.min(10, openaiKey.length())) + "...");
        log.info("Configured LLM provider: {}", configuredProvider);
        log.info("Available LLM providers: {}",
                availableProviders.stream()
                        .map(LlmProvider::getProviderName)
                        .toList());

        // Find the configured provider
        for (LlmProvider provider : availableProviders) {
            if (provider.getProviderName().equalsIgnoreCase(configuredProvider)) {
                if (provider.isAvailable()) {
                    log.info("Selected LLM provider: {} ({})",
                            provider.getProviderName(),
                            provider.getModelName());
                    return provider;
                } else {
                    log.warn("Configured provider {} is not available",
                            provider.getProviderName());
                }
            }
        }

        // Fallback: use first available provider
        for (LlmProvider provider : availableProviders) {
            if (provider.isAvailable()) {
                log.warn("Using fallback provider: {} (configured: {} was not available)",
                        provider.getProviderName(), configuredProvider);
                return provider;
            }
        }

        // No providers available
        throw new IllegalStateException(
                "No LLM providers available. Please configure at least one: " +
                        "ai.provider (openai|anthropic|ollama) and corresponding API keys"
        );
    }
}