package com.africevent.backend.service;

import com.africevent.backend.config.SmartPayProperties;
import com.africevent.backend.dto.SmartPaySession;
import com.africevent.backend.dto.SmartPaySessionRequest;
import com.africevent.backend.dto.SmartPayWebhookEvent;
import com.africevent.backend.exception.SmartPayException;
import com.africevent.backend.util.CryptoUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Client de l'API marchand SmartPay Checkout. Chaque requete est signee en
 * HMAC-SHA256 sur les octets exacts du corps envoye.
 */
@Slf4j
@Service
public class PaymentService {

    private static final Pattern SAFE_SESSION_ID = Pattern.compile("^[A-Za-z0-9_\\-]+$");

    private final SmartPayProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public PaymentService(SmartPayProperties properties,
                          ObjectMapper objectMapper,
                          RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .build();
    }

    /* ==============================
       API MARCHAND
    ============================== */
    public SmartPaySession createSession(SmartPaySessionRequest request) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Sérialisation de la session SmartPay impossible", e);
        }
        return parseSession(execute(HttpMethod.POST, "/merchant/v1/sessions", body));
    }

    public SmartPaySession getSession(String sessionId) {
        return parseSession(execute(HttpMethod.GET,
                "/merchant/v1/sessions/" + requireSafeId(sessionId), new byte[0]));
    }

    public void cancelSession(String sessionId) {
        execute(HttpMethod.POST,
                "/merchant/v1/sessions/" + requireSafeId(sessionId) + "/cancel", new byte[0]);
    }

    /* ==============================
       WEBHOOK
    ============================== */
    public boolean isWebhookConfigured() {
        return properties.hasWebhookSecret();
    }

    /**
     * Verifie l'en-tete SmartPay-Signature (t=timestamp,v1=hmac) sur le corps brut,
     * en comparaison a temps constant et avec tolerance sur l'horodatage.
     */
    public boolean verifyWebhookSignature(String signatureHeader, byte[] rawBody) {
        if (!properties.hasWebhookSecret() || signatureHeader == null || rawBody == null) {
            return false;
        }

        String timestamp = null;
        String signature = null;
        for (String part : signatureHeader.split(",")) {
            String[] keyValue = part.trim().split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            if ("t".equals(keyValue[0])) {
                timestamp = keyValue[1];
            } else if ("v1".equals(keyValue[0])) {
                signature = keyValue[1];
            }
        }
        if (timestamp == null || signature == null) {
            return false;
        }

        long signedAt;
        try {
            signedAt = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(Instant.now().getEpochSecond() - signedAt) > properties.getWebhookToleranceSeconds()) {
            return false;
        }

        byte[] prefix = (timestamp + ".").getBytes(StandardCharsets.UTF_8);
        byte[] signedPayload = new byte[prefix.length + rawBody.length];
        System.arraycopy(prefix, 0, signedPayload, 0, prefix.length);
        System.arraycopy(rawBody, 0, signedPayload, prefix.length, rawBody.length);

        String expected = CryptoUtil.hmacSha256Hex(properties.getWebhookSecret(), signedPayload);
        return CryptoUtil.constantTimeEquals(expected, signature.toLowerCase(Locale.ROOT));
    }

    public SmartPayWebhookEvent parseWebhookEvent(byte[] rawBody) {
        try {
            return objectMapper.readValue(rawBody, SmartPayWebhookEvent.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Corps de webhook invalide", e);
        }
    }

    /* ==============================
       HTTP SIGNE
    ============================== */
    private byte[] execute(HttpMethod method, String path, byte[] body) {
        if (!properties.hasApiCredentials()) {
            log.error("Les identifiants SmartPay ne sont pas configurés");
            throw new SmartPayException("Identifiants SmartPay non configurés", 0, "NOT_CONFIGURED", null, null);
        }

        URI uri = URI.create(trimTrailingSlash(properties.getBaseUrl()) + path);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String canonical = timestamp + "\n" + method.name() + "\n" + uri.getRawPath() + "\n"
                + CryptoUtil.sha256Hex(body);
        String signature = CryptoUtil.hmacSha256Hex(properties.getApiSecret(), canonical);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("X-SmartPay-Key-Id", properties.getKeyId());
        headers.set("X-SmartPay-Timestamp", timestamp);
        headers.set("X-SmartPay-Signature", signature);

        HttpEntity<byte[]> entity = new HttpEntity<>(body.length == 0 ? null : body, headers);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(uri, method, entity, byte[].class);
            return response.getBody();
        } catch (RestClientResponseException e) {
            throw toException(e);
        } catch (RestClientException e) {
            log.warn("SmartPay injoignable : {}", e.getMessage());
            throw new SmartPayException("Service SmartPay injoignable", 0, "UNREACHABLE", null, e);
        }
    }

    private SmartPaySession parseSession(byte[] body) {
        if (body == null || body.length == 0) {
            throw new SmartPayException("Réponse SmartPay vide", 0, "EMPTY_RESPONSE", null, null);
        }
        try {
            return objectMapper.readValue(body, SmartPaySession.class);
        } catch (IOException e) {
            throw new SmartPayException("Réponse SmartPay illisible", 0, "INVALID_RESPONSE", null, e);
        }
    }

    private SmartPayException toException(RestClientResponseException e) {
        int status = e.getRawStatusCode();
        String code = null;
        String requestId = null;
        String message = null;
        try {
            JsonNode root = objectMapper.readTree(e.getResponseBodyAsByteArray());
            if (root != null) {
                JsonNode error = root.path("error");
                code = textOrNull(error.path("code"));
                requestId = textOrNull(error.path("requestId"));
                message = textOrNull(error.path("message"));
            }
        } catch (IOException ignored) {
            // Corps d'erreur non JSON : seul le statut HTTP est exploitable.
        }

        if (status == 401 || status == 403) {
            log.error("Erreur SmartPay d'authentification : statut={} code={} requestId={} message={}",
                    status, code, requestId, message);
        } else {
            log.warn("Erreur SmartPay : statut={} code={} requestId={} message={}",
                    status, code, requestId, message);
        }
        return new SmartPayException("Erreur SmartPay " + status, status, code, requestId, e);
    }

    private String requireSafeId(String sessionId) {
        if (sessionId == null || !SAFE_SESSION_ID.matcher(sessionId).matches()) {
            throw new IllegalArgumentException("Identifiant de session SmartPay invalide");
        }
        return sessionId;
    }

    private String textOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}