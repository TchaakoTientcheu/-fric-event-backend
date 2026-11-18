package com.africevent.backend.service;

import com.africevent.backend.model.Event;
import com.africevent.backend.model.TicketType;
import com.africevent.backend.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventStatusService {

    private final EventRepository eventRepository;

    /**
     * Recalcule le statut d'un événement :
     * - Si le stock total des billets disponibles est 0 et que le statut n'est ni ANNULE ni TERMINE -> passe à TERMINE.
     * - Si le stock total redevient > 0 et que le statut est TERMINE -> repasse à ACTIF.
     * - Ne touche pas aux événements ANNULE.
     */
    public void recomputeStatut(String eventId) {
        Event event = eventRepository.findById(eventId).orElse(null);
        if (event == null) return;

        // Ne pas toucher aux événements annulés manuellement
        if ("ANNULE".equals(event.getStatut())) return;

        int stockTotal = event.getTicketTypes().stream()
                .mapToInt(t -> t.getQuantiteDisponible() == null ? 0 : t.getQuantiteDisponible())
                .sum();

        String nouveauStatut;
        if (stockTotal <= 0) {
            nouveauStatut = "TERMINE";
        } else {
            nouveauStatut = "ACTIF";
        }

        if (!nouveauStatut.equals(event.getStatut())) {
            event.setStatut(nouveauStatut);
            eventRepository.save(event);
        }
    }

    /**
     * Recalcule le stock total de billets d'un événement (utile pour l'admin).
     */
    public int stockTotal(Event event) {
        if (event == null || event.getTicketTypes() == null) return 0;
        return event.getTicketTypes().stream()
                .mapToInt(t -> t.getQuantiteDisponible() == null ? 0 : t.getQuantiteDisponible())
                .sum();
    }

    /**
     * Vérifie si un événement est éligible à la vente.
     */
    public boolean estAchetable(Event event) {
        return event != null && "ACTIF".equals(event.getStatut());
    }

    /**
     * Vérifie si un ticketType est disponible.
     */
    public boolean ticketTypeDisponible(TicketType ticket) {
        return ticket != null
                && ticket.getQuantiteDisponible() != null
                && ticket.getQuantiteDisponible() > 0;
    }
}