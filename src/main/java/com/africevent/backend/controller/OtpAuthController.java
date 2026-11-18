package com.africevent.backend.controller;

import com.africevent.backend.dto.ApiMessageResponse;
import com.africevent.backend.dto.AuthResponse;
import com.africevent.backend.dto.OtpRequest;
import com.africevent.backend.dto.OtpVerifyRequest;
import com.africevent.backend.model.User;
import com.africevent.backend.service.CheckoutService;
import com.africevent.backend.service.OtpAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/** Connexion d'un client par code recu par email, pour retrouver ses billets. */
@RestController
@RequestMapping("/api/auth/otp")
@RequiredArgsConstructor
public class OtpAuthController {

    private static final String OTP_SENT = "Si l'adresse est valide, un code de vérification vient d'être envoyé.";

    private final OtpAccountService otpAccountService;
    private final CheckoutService checkoutService;

    @PostMapping("/request")
    public ResponseEntity<ApiMessageResponse> request(@Valid @RequestBody OtpRequest req) {
        otpAccountService.requestOtp(req.getEmail());
        return ResponseEntity.ok(new ApiMessageResponse(OTP_SENT));
    }

    @PostMapping("/verify")
    public ResponseEntity<AuthResponse> verify(@Valid @RequestBody OtpVerifyRequest req) {
        User user = otpAccountService.authenticate(req.getEmail(), req.getCode());
        return ResponseEntity.ok(checkoutService.toAuth(user));
    }
}