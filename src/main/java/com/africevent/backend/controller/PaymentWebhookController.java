package com.africevent.backend.controller;

import com.africevent.backend.dto.SmartPayWebhookEvent;
import com.africevent.backend.service.OrderPaymentService;
import com.africevent.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentService paymentService;
    private final OrderPaymentService orderPaymentService;

    /**
     * Le corps est reçu en octets bruts : la signature SmartPay porte sur ces octets exacts.
     * Une signature invalide est refusée (400). Une erreur interne renvoie 500 afin que
     * SmartPay rejoue l'événement ; le traitement est idempotent.
     */
    @PostMapping("/smartpay")
    public ResponseEntity<Void> smartPayWebhook(
            @RequestHeader(value = "SmartPay-Signature", required = false) String signature,
            @RequestBody byte[] rawBody) {

        if (!paymentService.isWebhookConfigured()) {
            log.error("Webhook SmartPay reçu alors que le secret webhook n'est pas configuré");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (!paymentService.verifyWebhookSignature(signature, rawBody)) {
            log.warn("Webhook SmartPay rejeté : signature invalide");
            return ResponseEntity.badRequest().build();
        }

        SmartPayWebhookEvent event;
        try {
            event = paymentService.parseWebhookEvent(rawBody);
        } catch (IllegalArgumentException e) {
            log.warn("Webhook SmartPay rejeté : corps illisible");
            return ResponseEntity.badRequest().build();
        }

        orderPaymentService.handleWebhook(event);
        return ResponseEntity.ok().build();
    }
}