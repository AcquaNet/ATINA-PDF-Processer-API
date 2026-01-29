package com.atina.invoice.api.config;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;

/**
 * RestClient configuration with HTTP and HTTPS support
 *
 * Uses Apache HttpClient 5 as the underlying HTTP client for proper SSL support
 * including self-signed certificate acceptance and hostname verification bypass.
 *
 * SECURITY NOTE: Accepts all SSL certificates (including self-signed)
 * Suitable for development/testing. For production, consider proper certificate validation.
 */
@Configuration
public class RestClientConfig {

    /**
     * Create RestClient bean with HTTP and HTTPS support
     *
     * Features:
     * - Supports both HTTP and HTTPS protocols
     * - Accepts all SSL certificates (including self-signed)
     * - No hostname verification (for self-signed certificates)
     * - Connection pooling for better performance
     * - Configurable timeouts
     *
     * @param builder RestClient.Builder injected by Spring
     * @return RestClient configured for HTTP/HTTPS
     * @throws Exception if SSL context creation fails
     */
    @Bean
    public RestClient restClient(RestClient.Builder builder) throws Exception {
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

        // Create request factory with Apache HttpClient 5
        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectTimeout(30000);
        requestFactory.setConnectionRequestTimeout(30000);

        return builder
                .requestFactory(requestFactory)
                .build();
    }
}
