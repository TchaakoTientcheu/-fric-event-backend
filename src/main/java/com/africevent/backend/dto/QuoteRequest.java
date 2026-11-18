package com.africevent.backend.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

@Data
public class QuoteRequest {

    @NotBlank(message = "L'événement est obligatoire")
    private String eventId;

    @NotBlank(message = "Le type de ticket est obligatoire")
    private String ticketTypeId;

    @Min(value = 1, message = "La quantité doit être supérieure ou égale à 1")
    private int quantite;
}