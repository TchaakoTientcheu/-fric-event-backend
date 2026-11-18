package com.africevent.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class TicketResponse {
    private String id;
    private String orderId;
    private String eventId;
    private String codeQrUnique;
    /** Image PNG du QR code, encodee en data URI (data:image/png;base64,...). */
    private String qrImageUrl;
    private boolean estUtilise;
    private LocalDateTime dateGeneration;
    private String eventTitre;
    private String eventLieu;
    private LocalDateTime eventDate;
    private String eventImageUrl;
    private String ticketTypeNom;
    private String referenceCommande;
    private String titulaireNom;
}