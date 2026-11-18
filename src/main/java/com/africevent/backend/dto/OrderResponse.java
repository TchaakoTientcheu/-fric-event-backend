package com.africevent.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class OrderResponse {
    private String id;
    private String referenceTransaction;
    private String statutPaiement;
    private String methodePaiement;
    private Double montantInitial;
    private Double montantTotalPaye;
    private Double montantRemise;
    private Integer quantite;
    private String eventId;
    private String eventTitre;
    private String ticketTypeNom;
    private LocalDateTime dateCreation;
}