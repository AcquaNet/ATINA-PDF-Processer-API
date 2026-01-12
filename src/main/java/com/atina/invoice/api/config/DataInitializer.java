package com.atina.invoice.api.config;

import com.atina.invoice.api.model.User;
import com.atina.invoice.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

/**
 * Database initialization configuration
 * Creates initial users programmatically instead of using data.sql
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            log.info("Initializing database with default users...");

            // Check if users already exist
            if (userRepository.count() > 0) {
                log.info("Users already exist, skipping initialization");
                return;
            }

            // Create admin user
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setEmail("admin@example.com");
            admin.setFullName("Administrator");
            admin.setEnabled(true);
            admin.setCreatedAt(Instant.now());
            userRepository.save(admin);
            log.info("Created admin user");

            // Create regular user
            User user = new User();
            user.setUsername("user");
            user.setPassword(passwordEncoder.encode("admin123"));
            user.setEmail("user@example.com");
            user.setFullName("Regular User");
            user.setEnabled(true);
            user.setCreatedAt(Instant.now());
            userRepository.save(user);
            log.info("Created regular user");

            log.info("Database initialization completed successfully");
        };
    }
}