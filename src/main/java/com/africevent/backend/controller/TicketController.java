package com.africevent.backend.controller;

import com.africevent.backend.dto.TicketResponse;
import com.africevent.backend.security.CurrentUser;
import com.africevent.backend.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    /** Tous les billets payés de l'utilisateur authentifié, avec leur QR code */
    @GetMapping("/mine")
    public ResponseEntity<List<TicketResponse>> getMine(Authentication authentication) {
        return ResponseEntity.ok(ticketService.getMyTickets(CurrentUser.id(authentication)));
    }

    /** Un billet précis (propriétaire ou administrateur) */
    @GetMapping("/{ticketId}")
    public ResponseEntity<TicketResponse> getOne(@PathVariable String ticketId,
                                                 Authentication authentication) {
        return ResponseEntity.ok(ticketService.getTicket(
                CurrentUser.id(authentication), CurrentUser.isAdmin(authentication), ticketId));
    }
}