package com.africevent.backend.dto;

import lombok.Data;

@Data
public class TicketTypeRequest {
    private String nom;
    private Double prix;
    private String description;
    private Integer quantiteDisponible;
}