package com.africevent.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "app.payment")
public class PaymentMethodProperties {

    /** Code SmartPay vers libelle affiche, dans l'ordre de declaration. */
    private Map<String, String> methods = new LinkedHashMap<>();

    public boolean isSupported(String code) {
        return code != null && methods.containsKey(code.trim().toUpperCase(Locale.ROOT));
    }
}