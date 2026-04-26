package com.raditomo.radiko;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "raditomo.radiko")
public record RadikoProperties(
        int downloadConcurrency,
        String baseUrl,
        String authKey,
        int authCacheHours,
        int httpConnectTimeoutSeconds,
        int httpReadTimeoutSeconds,
        int chunkRetry
) {
}
