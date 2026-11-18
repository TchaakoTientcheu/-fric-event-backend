package com.africevent.backend.controller;

import com.africevent.backend.dto.TicketTypeRequest;
import com.africevent.backend.model.Event;
import com.africevent.backend.service.AdminEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/events")
@RequiredArgsConstructor
public class AdminEventController {

    private final AdminEventService adminEventService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Event> create(
            @RequestParam String titre,
            @RequestParam String description,
            @RequestParam String dateEvent,
            @RequestParam String lieu,
            @RequestParam(value = "image", required = false) MultipartFile image) throws IOException {

        return ResponseEntity.ok(adminEventService.create(titre, description, dateEvent, lieu, image));
    }

    /**
     * Modification d'un evenement. L'image est optionnelle :
     * absente, l'image actuelle est conservee.
     */
    @PutMapping(value = "/{eventId}", consumes = "multipart/form-data")
    public ResponseEntity<Event> update(
            @PathVariable String eventId,
            @RequestParam String titre,
            @RequestParam String description,
            @RequestParam String dateEvent,
            @RequestParam String lieu,
            @RequestParam(value = "image", required = false) MultipartFile image) throws IOException {

        return ResponseEntity.ok(
                adminEventService.update(eventId, titre, description, dateEvent, lieu, image));
    }

    /** Corps attendu : { "statut": "ACTIF" | "TERMINE" | "ANNULE" } */
    @PatchMapping("/{eventId}/statut")
    public ResponseEntity<Event> updateStatut(
            @PathVariable String eventId,
            @RequestBody Map<String, String> body) {

        String statut = body == null ? null : body.get("statut");
        return ResponseEntity.ok(adminEventService.updateStatut(eventId, statut));
    }

    @PostMapping("/{eventId}/ticket-types")
    public ResponseEntity<Event> addTicketType(
            @PathVariable String eventId,
            @Valid @RequestBody TicketTypeRequest request) {

        return ResponseEntity.ok(adminEventService.addTicketType(eventId, request));
    }

    @PutMapping("/{eventId}/ticket-types/{ticketTypeId}")
    public ResponseEntity<Event> updateTicketType(
            @PathVariable String eventId,
            @PathVariable String ticketTypeId,
            @Valid @RequestBody TicketTypeRequest request) {

        return ResponseEntity.ok(adminEventService.updateTicketType(eventId, ticketTypeId, request));
    }

    @DeleteMapping("/{eventId}/ticket-types/{ticketTypeId}")
    public ResponseEntity<Event> deleteTicketType(
            @PathVariable String eventId,
            @PathVariable String ticketTypeId) {

        return ResponseEntity.ok(adminEventService.deleteTicketType(eventId, ticketTypeId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable String id) {
        adminEventService.delete(id);
        return ResponseEntity.ok(Collections.singletonMap("message", "Événement supprimé"));
    }
}