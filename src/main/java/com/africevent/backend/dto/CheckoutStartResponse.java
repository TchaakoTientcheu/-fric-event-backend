package com.africevent.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CheckoutStartResponse {
    private AuthResponse auth;
    private PaymentInitResponse payment;
}