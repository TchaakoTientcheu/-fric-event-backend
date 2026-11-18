package com.africevent.backend.service;

import com.africevent.backend.model.Event;
import com.africevent.backend.model.IssuedTicket;
import com.africevent.backend.model.Order;
import com.africevent.backend.model.OrderStatus;
import com.africevent.backend.model.User;
import com.africevent.backend.repository.EventRepository;
import com.africevent.backend.repository.IssuedTicketRepository;
import com.africevent.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketGenerationService {

    private final IssuedTicketRepository ticketRepository;
    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final MailService mailService;

    /**
     * Génère les billets manquants d'une commande payée. L'opération est idempotente :
     * une prise de verrou atomique garantit qu'un seul appel génère les billets, même si
     * le webhook et la vérification de statut arrivent simultanément. Le même appel envoie
     * ensuite les billets par email : l'email part donc une seule fois.
     */
    public void generateTickets(Order order) {
        Query claim = Query.query(Criteria.where("id").is(order.getId())
                .and("statutPaiement").is(OrderStatus.PAYE)
                .and("billetsGeneres").ne(true));
        long claimed = mongoTemplate.updateFirst(claim, Update.update("billetsGeneres", true), Order.class)
                .getModifiedCount();
        if (claimed == 0) {
            return;
        }

        List<IssuedTicket> saved;
        try {
            long existants = ticketRepository.countByOrderId(order.getId());
            int aGenerer = order.getQuantite() - (int) existants;

            List<IssuedTicket> tickets = new ArrayList<>();
            for (int i = 0; i < aGenerer; i++) {
                IssuedTicket ticket = new IssuedTicket();
                ticket.setOrderId(order.getId());
                ticket.setCodeQrUnique(UUID.randomUUID().toString());
                ticket.setEstUtilise(false);
                ticket.setDateGeneration(LocalDateTime.now());
                tickets.add(ticket);
            }
            saved = ticketRepository.saveAll(tickets);
        } catch (RuntimeException e) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("id").is(order.getId())),
                    Update.update("billetsGeneres", false),
                    Order.class);
            throw e;
        }

        notifyBuyer(order, saved);
    }

    /** Un échec d'envoi ne remet jamais en cause les billets : ils restent disponibles dans l'espace client. */
    private void notifyBuyer(Order order, List<IssuedTicket> tickets) {
        if (tickets.isEmpty() || order.getUserId() == null) {
            return;
        }
        try {
            User user = userRepository.findById(order.getUserId()).orElse(null);
            if (user == null || user.getEmail() == null || user.getEmail().trim().isEmpty()) {
                return;
            }
            Event event = order.getEventId() == null
                    ? null
                    : eventRepository.findById(order.getEventId()).orElse(null);
            mailService.sendTickets(user, order, event, tickets);
        } catch (RuntimeException e) {
            log.error("Envoi des billets impossible pour la commande {} : {}",
                    order.getId(), e.getClass().getSimpleName());
        }
    }
}