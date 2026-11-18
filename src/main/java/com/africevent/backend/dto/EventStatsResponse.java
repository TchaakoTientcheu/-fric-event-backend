package com.africevent.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class EventStatsResponse {
    private long totalEvenements;
    private long totalEvenementsActifs;
    private long totalEvenementsTermines;
    private long totalEvenementsAnnules;
    private long totalTicketsDisponibles;
    private long totalTicketsVendus;
    private double chiffreAffairesTotal;
    private long totalCommandes;
    private long totalUtilisateurs;
    private List<PaymentMethodStat> repartitionParMethode;
    private List<EventTopStat> topEvenements;

    @Data
    @Builder
    public static class PaymentMethodStat {
        private String methode;
        private long nombreCommandes;
        private double montantTotal;
    }

    @Data
    @Builder
    public static class EventTopStat {
        private String eventId;
        private String titre;
        private long ticketsVendus;
        private double chiffreAffaires;
    }
}