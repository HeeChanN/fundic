package io.fundic.fundic_server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kis")
public record KisProperties(
        String mockBaseUrl,
        String mockAppKey,
        String mockAppSecret,
        String tokenEndpoint,
        long refreshSkewSeconds
) {}