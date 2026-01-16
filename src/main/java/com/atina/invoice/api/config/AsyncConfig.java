package com.atina.invoice.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuración para procesamiento asíncrono de jobs
 *
 * Proporciona un thread pool dedicado para procesamiento de extracciones en background.
 *
 * @author Atina Team
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Thread pool para procesamiento asíncrono de jobs
     *
     * Configuración recomendada:
     * - Tráfico bajo-medio: core=5, max=10
     * - Tráfico alto: core=10, max=20
     */
    @Bean(name = "jobExecutor")
    public Executor jobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("job-executor-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
}
