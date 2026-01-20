package com.atina.invoice.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuración para habilitar scheduling
 * 
 * Esto permite que @Scheduled funcione en la aplicación
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // Spring automáticamente habilita el scheduling
    // con esta configuración
}
