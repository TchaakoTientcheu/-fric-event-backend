package com.africevent.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "payment_promos")
public class PaymentPromo {
    @Id
    private String id;
    private String eventId;
    private String methodePaiement;
    private Double pourcentageReduction;
    private Boolean actif;
}