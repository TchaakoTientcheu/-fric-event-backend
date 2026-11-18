package com.africevent.backend.controller;

import com.africevent.backend.dto.ApiMessageResponse;
import com.africevent.backend.dto.CheckoutStartRequest;
import com.africevent.backend.dto.CheckoutStartResponse;
import com.africevent.backend.dto.OtpRequest;
import com.africevent.backend.dto.QuoteRequest;
import com.africevent.backend.dto.QuoteResponse;
import com.africevent.backend.service.CheckoutService;
import com.africevent.backend.service.OtpAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/** Achat de billets sans compte prealable. Ces routes sont publiques. */
@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private static final String OTP_SENT = "Si l'adresse est valide, un code de vérification vient d'être envoyé.";

    private final CheckoutService checkoutService;
    private final OtpAccountService otpAccountService;

    /** Prix pour chaque moyen de paiement, remises comprises (calcul serveur). */
    @PostMapping("/quote")
    public ResponseEntity<QuoteResponse> quote(@Valid @RequestBody QuoteRequest req) {
        return ResponseEntity.ok(checkoutService.quote(req));
    }

    /** Envoie un code de verification a l'adresse indiquee. */
    @PostMapping("/otp")
    public ResponseEntity<ApiMessageResponse> requestOtp(@Valid @RequestBody OtpRequest req) {
        otpAccountService.requestOtp(req.getEmail());
        return ResponseEntity.ok(new ApiMessageResponse(OTP_SENT));
    }

    /** Verifie le code, cree commande et session SmartPay, retourne le token et l'URL de paiement. */
    @PostMapping
    public ResponseEntity<CheckoutStartResponse> start(@Valid @RequestBody CheckoutStartRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(checkoutService.start(req));
    }
}