package com.africevent.backend.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class CreateOrderRequest {

    @NotBlank(message = "L'événement est obligatoire")
    private String eventId;

    @NotBlank(message = "Le type de ticket est obligatoire")
    private String ticketTypeId;

    @Min(value = 1, message = "La quantité doit être supérieure ou égale à 1")
    private int quantite;

    /**
     * Moyen choisi par le client (SMARTPAY, COLLECT_MTN, COLLECT_ORANGE...). Il determine la remise
     * appliquee par le serveur et restreint les moyens proposes sur la page SmartPay.
     */
    @NotBlank(message = "Le moyen de paiement est obligatoire")
    @Size(max = 50, message = "Le moyen de paiement est invalide")
    private String methodePaiement;

    @Pattern(regexp = "^(\\+?[0-9 ]{8,20})?$", message = "Le numéro de téléphone est invalide")
    private String phoneNumber;
}