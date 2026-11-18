package com.africevent.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentInitResponse {
    private String orderId;
    private String referenceTransaction;
    private String paymentUrl;
    private String statut;
    private String message;
}