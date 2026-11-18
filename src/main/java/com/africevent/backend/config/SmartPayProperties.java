package com.africevent.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "smartpay")
public class SmartPayProperties {

    private String baseUrl;
    private String keyId;
    private String apiSecret;
    private String webhookSecret;
    private String currency = "XAF";
    private String locale = "fr";
    private String returnUrl;
    private String cancelUrl;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 30000;
    private long webhookToleranceSeconds = 300;

    public boolean hasApiCredentials() {
        return notBlank(baseUrl) && notBlank(keyId) && notBlank(apiSecret);
    }

    public boolean hasWebhookSecret() {
        return notBlank(webhookSecret);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}