package com.africevent.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SmartPaySession {
    private String id;
    private String status;
    private Double amount;
    private String currency;
    private String merchantRef;
    private String checkoutUrl;
    private Double paidAmount;
    private Double discountAmount;
    private String paidWith;
    private String transactionNo;
}