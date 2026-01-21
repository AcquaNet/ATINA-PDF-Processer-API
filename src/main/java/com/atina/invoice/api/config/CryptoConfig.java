package com.atina.invoice.api.config;

import com.atina.invoice.api.security.AesGcmCrypto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;

@Configuration
public class CryptoConfig {

    @Bean
    public AesGcmCrypto aesGcmCrypto(@Value("${email.crypt.key-base64}") String keyBase64) {
        if (keyBase64 == null || keyBase64.isBlank()) {
            throw new IllegalStateException("Missing property email.crypt.key-base64");
        }
        byte[] key = Base64.getDecoder().decode(keyBase64);
        return new AesGcmCrypto(key);
    }
}
