package com.africevent.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class QuoteResponse {
    private String eventId;
    private String eventTitre;
    private String ticketTypeId;
    private String ticketTypeNom;
    private int quantite;
    private double prixUnitaire;
    private long montantInitial;
    private String devise;
    private List<Option> options;

    /** Prix pour un moyen de paiement donne. */
    @Data
    @Builder
    public static class Option {
        private String code;
        private String libelle;
        private double tauxReduction;
        private long montantRemise;
        private long montantFinal;
    }
}