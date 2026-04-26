package com.raditomo.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "raditomo.notification")
public record NotificationProperties(
        boolean enabled,
        String from,
        String subjectPrefix
) {
}
