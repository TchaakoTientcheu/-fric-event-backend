package com.africevent.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "orders")
@CompoundIndex(name = "statut_date_idx", def = "{'statutPaiement': 1, 'dateAchat': 1}")
public class Order {
    @Id
    private String id;
    @Indexed
    private String userId;
    private String eventId;
    private String ticketTypeId;
    private String ticketTypeNom;
    private Integer quantite;
    private String methodePaiement;
    private String telephoneClient;
    private Double prixUnitaireInitial;
    /** Pourcentage de remise applique pour le moyen de paiement choisi. */
    private Double tauxReduction;
    /** Montant de la remise en XAF. */
    private Double reductionAppliquee;
    /** Montant demande a SmartPay (prix remise). */
    private Double montantTotalPaye;
    private String statutPaiement; // EN_ATTENTE, PAYE, ECHEC
    private String referenceTransaction;
    private LocalDateTime dateAchat;

    // Paiement SmartPay
    private String smartPaySessionId;
    private String checkoutUrl;
    private String moyenPaiementUtilise;
    private String transactionNo;
    private Double montantEncaisse;
    private LocalDateTime datePaiement;

    // Suivi interne
    private Boolean billetsGeneres;
    private String anomalie;
}