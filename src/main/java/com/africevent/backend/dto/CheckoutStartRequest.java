package com.africevent.backend.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class CheckoutStartRequest {

    @NotBlank(message = "L'événement est obligatoire")
    private String eventId;

    @NotBlank(message = "Le type de ticket est obligatoire")
    private String ticketTypeId;

    @Min(value = 1, message = "La quantité doit être supérieure ou égale à 1")
    private int quantite;

    @NotBlank(message = "Le moyen de paiement est obligatoire")
    @Size(max = 50, message = "Le moyen de paiement est invalide")
    private String methodePaiement;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Le format de l'email est invalide")
    @Size(max = 150, message = "L'email ne doit pas dépasser 150 caractères")
    private String email;

    @NotBlank(message = "Le code est obligatoire")
    @Pattern(regexp = "^[0-9]{4,9}$", message = "Le code est invalide")
    private String code;

    @Pattern(regexp = "^(\\+?[0-9 ]{8,20})?$", message = "Le numéro de téléphone est invalide")
    private String phoneNumber;
}