package com.africevent.backend.controller;

import com.africevent.backend.config.PaymentMethodProperties;
import com.africevent.backend.dto.PaymentMethodResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentMethodController {

    private final PaymentMethodProperties paymentMethods;

    /** Moyens de paiement proposés : source unique pour le checkout et la configuration des promotions */
    @GetMapping("/methods")
    public List<PaymentMethodResponse> getMethods() {
        return paymentMethods.getMethods().entrySet().stream()
                .map(entry -> PaymentMethodResponse.builder()
                        .code(entry.getKey())
                        .libelle(entry.getValue())
                        .build())
                .collect(Collectors.toList());
    }
}