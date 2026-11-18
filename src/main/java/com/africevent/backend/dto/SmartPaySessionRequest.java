package com.africevent.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Corps de POST {BASE}/merchant/v1/sessions (guide SmartPay Checkout, section 4).
 * Les champs non renseignés ne sont pas envoyés : le JSON contient uniquement ce que l'API attend.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SmartPaySessionRequest {

    /** Montant entier en XAF, sans décimales */
    private Long amount;
    private String currency;
    /** Référence de commande unique (64 caractères max) : sert aussi d'idempotence */
    private String merchantRef;
    private String description;
    private String returnUrl;
    private String cancelUrl;
    private Customer customer;
    private String locale;
    private Integer expiresInMinutes;
    /** Restriction des moyens proposés : laissé vide, le client choisit sur la page SmartPay */
    private List<String> paymentMethods;
    private Map<String, String> metadata;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Customer {
        private String email;
        private String phone;
    }
}