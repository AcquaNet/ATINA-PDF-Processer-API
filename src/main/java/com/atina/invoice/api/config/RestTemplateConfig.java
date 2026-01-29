package com.atina.invoice.api.config;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.time.Duration;

/**
 * RestTemplate configuration with HTTP and HTTPS support
 * Configures HTTP client for external API calls including webhook delivery
 *
 * SECURITY NOTE: This configuration accepts all SSL certificates (including self-signed ones)
 * which is suitable for development and testing. For production, consider using proper
 * certificate validation or configure specific trusted certificates.
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Create RestTemplate bean with HTTP and HTTPS support
     *
     * Features:
     * - Supports both HTTP and HTTPS protocols
     * - Accepts all SSL certificates (including self-signed)
     * - No hostname verification (for self-signed certificates)
     * - Connection pooling for better performance
     * - Configurable timeouts
     *
     * @return RestTemplate configured for HTTP/HTTPS
     * @throws Exception if SSL context creation fails
     */
    @Bean
    public RestTemplate restTemplate() throws Exception {
        // Create SSL context that trusts all certificates
        SSLContext sslContext = SSLContextBuilder.create()
                .loadTrustMaterial(new TrustAllStrategy())
                .build();

        // Create SSL connection socket factory with no hostname verification
        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                sslContext,
                NoopHostnameVerifier.INSTANCE
        );

        // Create connection manager with SSL support
        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(sslSocketFactory)
                        .build();

        // Create HTTP client with SSL support
        HttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();

        // Create request factory with HTTP client and set all timeouts directly
        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        requestFactory.setConnectTimeout(30000); // 30 seconds
        requestFactory.setConnectionRequestTimeout(30000); // 30 seconds

        // Create RestTemplate with the request factory
        RestTemplate restTemplate = new RestTemplate(requestFactory);

        return restTemplate;
    }
}
