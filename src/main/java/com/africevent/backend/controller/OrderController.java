package com.africevent.backend.controller;

import com.africevent.backend.dto.CreateOrderRequest;
import com.africevent.backend.dto.OrderResponse;
import com.africevent.backend.dto.PaymentInitResponse;
import com.africevent.backend.dto.TicketResponse;
import com.africevent.backend.security.CurrentUser;
import com.africevent.backend.service.OrderPaymentService;
import com.africevent.backend.service.OrderService;
import com.africevent.backend.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderPaymentService orderPaymentService;
    private final TicketService ticketService;

    /** Créer une commande (statut EN_ATTENTE) pour l'utilisateur authentifié */
    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest req,
                                                Authentication authentication) {
        OrderResponse response = orderService.createOrder(CurrentUser.id(authentication), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Créer la session de paiement SmartPay et retourner l'URL de la page de paiement */
    @PostMapping("/{orderId}/pay")
    public ResponseEntity<PaymentInitResponse> pay(@PathVariable String orderId,
                                                   Authentication authentication) {
        return ResponseEntity.ok(orderPaymentService.initiatePayment(CurrentUser.id(authentication), orderId));
    }

    /** Abandon d'une commande non payée : annule la session SmartPay ouverte et libère le stock */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancel(@PathVariable String orderId,
                                                Authentication authentication) {
        return ResponseEntity.ok(orderPaymentService.cancelOrder(CurrentUser.id(authentication), orderId));
    }

    /** Commandes de l'utilisateur authentifié */
    @GetMapping("/mine")
    public ResponseEntity<List<OrderResponse>> getMine(Authentication authentication) {
        return ResponseEntity.ok(orderService.getOrdersByUser(CurrentUser.id(authentication)));
    }

    /** Détail d'une commande */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId,
                                                  Authentication authentication) {
        return ResponseEntity.ok(orderService.getOrder(
                CurrentUser.id(authentication), CurrentUser.isAdmin(authentication), orderId));
    }

    /** Statut à jour : vérifie la session SmartPay côté serveur tant que la commande est en attente */
    @GetMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> getStatus(@PathVariable String orderId,
                                                   Authentication authentication) {
        return ResponseEntity.ok(orderPaymentService.refreshStatus(
                CurrentUser.id(authentication), CurrentUser.isAdmin(authentication), orderId));
    }

    /** Tickets d'une commande payée */
    @GetMapping("/{orderId}/tickets")
    public ResponseEntity<List<TicketResponse>> getTickets(@PathVariable String orderId,
                                                           Authentication authentication) {
        return ResponseEntity.ok(ticketService.getTicketsByOrder(
                CurrentUser.id(authentication), CurrentUser.isAdmin(authentication), orderId));
    }

    /** Commandes d'un utilisateur (réservé à l'utilisateur lui-même ou à un administrateur) */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<OrderResponse>> getByUser(@PathVariable String userId,
                                                         Authentication authentication) {
        if (!CurrentUser.isAdmin(authentication) && !userId.equals(CurrentUser.id(authentication))) {
            throw new AccessDeniedException("Accès refusé");
        }
        return ResponseEntity.ok(orderService.getOrdersByUser(userId));
    }
}