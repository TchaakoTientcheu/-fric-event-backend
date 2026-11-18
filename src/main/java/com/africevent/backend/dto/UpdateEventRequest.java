package com.africevent.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateEventRequest {
    private String titre;
    private String description;
    private LocalDateTime dateEvent;
    private String lieu;
    private String imageUrl;
    private String statut;
}