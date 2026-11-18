package com.africevent.backend.service;

import com.africevent.backend.dto.TicketTypeRequest;
import com.africevent.backend.dto.UpdateEventRequest;
import com.africevent.backend.exception.ResourceNotFoundException;
import com.africevent.backend.model.Event;
import com.africevent.backend.model.TicketType;
import com.africevent.backend.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminEventService {

    private static final String STATUT_ACTIF = "ACTIF";
    private static final Set<String> STATUTS_AUTORISES = Set.of("ACTIF", "TERMINE", "ANNULE");

    private static final String MSG_EVENT_INTROUVABLE = "Événement introuvable";
    private static final String MSG_TICKET_INTROUVABLE = "Type de billet introuvable";

    private final EventRepository eventRepository;
    private final FileStorageService fileStorageService;
    private final EventStatusService eventStatusService;

    /* ==============================
       CRÉATION
    ============================== */
    public Event create(String titre, String description, String dateEventIso,
                        String lieu, MultipartFile image) throws IOException {

        LocalDateTime date = parseDate(dateEventIso);
        String imageUrl = hasFile(image) ? fileStorageService.store(image) : null;

        Event event = new Event();
        event.setTitre(titre);
        event.setDescription(description);
        event.setDateEvent(date);
        event.setLieu(lieu);
        event.setImageUrl(imageUrl);
        event.setStatut(STATUT_ACTIF);

        return eventRepository.save(event);
    }

    /* ==============================
       MODIFICATION (formulaire admin : champs + image optionnelle)
       Le statut et les billets ne sont pas touches ici.
    ============================== */
    public Event update(String eventId, String titre, String description,
                        String dateEventIso, String lieu, MultipartFile image) throws IOException {

        Event event = findEvent(eventId);

        if (titre == null || titre.trim().isEmpty() || lieu == null || lieu.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le titre et le lieu sont obligatoires.");
        }

        LocalDateTime date = parseDate(dateEventIso);

        event.setTitre(titre.trim());
        event.setDescription(description);
        event.setDateEvent(date);
        event.setLieu(lieu.trim());

        if (hasFile(image)) {
            event.setImageUrl(fileStorageService.store(image));
        }

        return eventRepository.save(event);
    }

    /* ==============================
       MISE À JOUR PARTIELLE (conservee, version JSON)
    ============================== */
    public Event update(String eventId, UpdateEventRequest req) {
        Event event = findEvent(eventId);

        if (req.getTitre() != null) event.setTitre(req.getTitre());
        if (req.getDescription() != null) event.setDescription(req.getDescription());
        if (req.getDateEvent() != null) event.setDateEvent(req.getDateEvent());
        if (req.getLieu() != null) event.setLieu(req.getLieu());
        if (req.getImageUrl() != null) event.setImageUrl(req.getImageUrl());
        if (req.getStatut() != null) event.setStatut(req.getStatut());

        return eventRepository.save(event);
    }

    /* ==============================
       CHANGEMENT DE STATUT
    ============================== */
    public Event updateStatut(String eventId, String statut) {
        if (statut == null || statut.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le statut est obligatoire.");
        }

        String normalise = statut.trim().toUpperCase();
        if (!STATUTS_AUTORISES.contains(normalise)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Statut invalide. Valeurs autorisées : ACTIF, TERMINE, ANNULE.");
        }

        Event event = findEvent(eventId);
        event.setStatut(normalise);
        return eventRepository.save(event);
    }

    /* ==============================
       SUPPRESSION
    ============================== */
    public void delete(String eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException(MSG_EVENT_INTROUVABLE);
        }
        eventRepository.deleteById(eventId);
    }

    /* ==============================
       TICKET TYPES
    ============================== */
    public Event addTicketType(String eventId, TicketTypeRequest req) {
        Event event = findEvent(eventId);

        TicketType ticket = new TicketType();
        ticket.setId(UUID.randomUUID().toString());
        ticket.setNom(req.getNom());
        ticket.setPrix(req.getPrix());
        ticket.setDescription(req.getDescription());
        ticket.setQuantiteDisponible(req.getQuantiteDisponible() != null ? req.getQuantiteDisponible() : 0);

        event.getTicketTypes().add(ticket);
        Event saved = eventRepository.save(event);

        eventStatusService.recomputeStatut(eventId);
        return eventRepository.findById(eventId).orElse(saved);
    }

    public Event updateTicketType(String eventId, String ticketTypeId, TicketTypeRequest req) {
        Event event = findEvent(eventId);

        TicketType ticket = event.getTicketTypes().stream()
                .filter(t -> t.getId().equals(ticketTypeId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(MSG_TICKET_INTROUVABLE));

        if (req.getNom() != null) ticket.setNom(req.getNom());
        if (req.getPrix() != null) ticket.setPrix(req.getPrix());
        if (req.getDescription() != null) ticket.setDescription(req.getDescription());
        if (req.getQuantiteDisponible() != null) ticket.setQuantiteDisponible(req.getQuantiteDisponible());

        Event saved = eventRepository.save(event);
        eventStatusService.recomputeStatut(eventId);
        return eventRepository.findById(eventId).orElse(saved);
    }

    public Event deleteTicketType(String eventId, String ticketTypeId) {
        Event event = findEvent(eventId);

        boolean removed = event.getTicketTypes()
                .removeIf(t -> t.getId().equals(ticketTypeId));

        if (!removed) {
            throw new ResourceNotFoundException(MSG_TICKET_INTROUVABLE);
        }

        Event saved = eventRepository.save(event);
        eventStatusService.recomputeStatut(eventId);
        return eventRepository.findById(eventId).orElse(saved);
    }

    /* ==============================
       OUTILS
    ============================== */
    private Event findEvent(String eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(MSG_EVENT_INTROUVABLE));
    }

    private boolean hasFile(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    private LocalDateTime parseDate(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La date de l'événement est invalide.");
        }
    }
}