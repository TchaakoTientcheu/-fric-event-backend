package com.africevent.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "issued_tickets")
public class IssuedTicket {
    @Id
    private String id;
    private String orderId;
    @Indexed(unique = true)
    private String codeQrUnique;
    private Boolean estUtilise = false;
    private LocalDateTime dateGeneration;
}