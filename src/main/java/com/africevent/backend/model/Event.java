package com.africevent.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "events")
public class Event {
    @Id
    private String id;
    private String titre;
    private String description;
    private LocalDateTime dateEvent;
    private String lieu;
    private String imageUrl;
    private String statut; // ACTIF, TERMINE, ANNULE
    private List<TicketType> ticketTypes = new ArrayList<>();
}