package com.africevent.backend.model;

import lombok.Data;

@Data
public class TicketType {
    private String id;
    private String nom; // Classic, VIP, VVIP
    private Double prix;
    private String description;
    private Integer quantiteDisponible;
}