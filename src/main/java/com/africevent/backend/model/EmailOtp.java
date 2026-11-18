package com.africevent.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Code de verification envoye par email. Un seul document par adresse :
 * un nouvel envoi remplace le precedent. Seule l'empreinte du code est stockee.
 */
@Data
@Document(collection = "email_otps")
public class EmailOtp {
    /** Adresse email normalisee (minuscules). */
    @Id
    private String id;
    private String codeHash;
    private LocalDateTime expiresAt;
    private LocalDateTime lastSentAt;
    private int attempts;
}