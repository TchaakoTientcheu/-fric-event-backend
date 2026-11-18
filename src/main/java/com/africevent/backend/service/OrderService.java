package com.africevent.backend.service;

import com.africevent.backend.config.PaymentMethodProperties;
import com.africevent.backend.dto.CreateOrderRequest;
import com.africevent.backend.dto.OrderResponse;
import com.africevent.backend.exception.BadRequestException;
import com.africevent.backend.exception.ConflictException;
import com.africevent.backend.exception.ResourceNotFoundException;
import com.africevent.backend.model.Event;
import com.africevent.backend.model.Order;
import com.africevent.backend.model.OrderStatus;
import com.africevent.backend.model.TicketType;
import com.africevent.backend.repository.EventRepository;
import com.africevent.backend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final EventRepository eventRepository;
    private final StockService stockService;
    private final EventStatusService eventStatusService;
    private final PricingService pricingService;
    private final PaymentMethodProperties paymentMethods;

    @Value("${app.order.max-quantity}")
    private int maxQuantity;

    /* ==============================
       CRÉATION DE COMMANDE
    ============================== */

    /**
     * Crée une commande EN_ATTENTE. Le client choisit son moyen de paiement avant de payer : le serveur
     * applique la remise configurée pour ce moyen et c'est ce montant qui est envoyé à SmartPay.
     * Le prix n'est jamais lu depuis le frontend.
     */
    public OrderResponse createOrder(String userId, CreateOrderRequest req) {
        checkQuantity(req.getQuantite());
        String methode = requireSupportedMethod(req.getMethodePaiement());
        Event event = loadPurchasableEvent(req.getEventId());
        TicketType ticket = findPurchasableTicket(event, req.getTicketTypeId());

        PricingService.Pricing pricing = pricingService.compute(
                event.getId(), ticket.getPrix(), req.getQuantite(), methode);
        if (pricing.getMontantFinal().signum() <= 0) {
            throw new BadRequestException("Le montant de la commande doit être supérieur à zéro");
        }

        if (!stockService.reserve(event.getId(), ticket.getId(), req.getQuantite())) {
            throw new ConflictException("Stock insuffisant pour ce type de ticket");
        }

        Order order = new Order();
        order.setUserId(userId);
        order.setEventId(event.getId());
        order.setTicketTypeId(ticket.getId());
        order.setTicketTypeNom(ticket.getNom());
        order.setQuantite(req.getQuantite());
        order.setMethodePaiement(methode);
        order.setTelephoneClient(normalizePhone(req.getPhoneNumber()));
        order.setPrixUnitaireInitial(ticket.getPrix());
        order.setTauxReduction(pricing.getTaux());
        order.setReductionAppliquee(pricing.getMontantRemise().doubleValue());
        order.setMontantTotalPaye(pricing.getMontantFinal().doubleValue());
        order.setStatutPaiement(OrderStatus.EN_ATTENTE);
        order.setReferenceTransaction(UUID.randomUUID().toString());
        order.setDateAchat(LocalDateTime.now());

        try {
            orderRepository.save(order);
        } catch (RuntimeException e) {
            stockService.release(event.getId(), ticket.getId(), req.getQuantite());
            throw e;
        }

        return toResponse(order, event);
    }

    /* ==============================
       VALIDATIONS PARTAGÉES (commande et devis)
    ============================== */
    public void checkQuantity(int quantite) {
        if (quantite > maxQuantity) {
            throw new BadRequestException("La quantité maximale par commande est de " + maxQuantity);
        }
    }

    /** Retourne le code normalisé du moyen de paiement, ou refuse un moyen inconnu. */
    public String requireSupportedMethod(String code) {
        if (code == null || !paymentMethods.isSupported(code)) {
            throw new BadRequestException("Ce moyen de paiement n'est pas disponible");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    public Event loadPurchasableEvent(String eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Événement introuvable"));

        if (!eventStatusService.estAchetable(event)) {
            throw new BadRequestException("Cet événement n'est plus disponible à la vente");
        }
        if (event.getDateEvent() != null && event.getDateEvent().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Cet événement est déjà passé");
        }
        return event;
    }

    public TicketType findPurchasableTicket(Event event, String ticketTypeId) {
        TicketType ticket = event.getTicketTypes().stream()
                .filter(t -> ticketTypeId != null && ticketTypeId.equals(t.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Type de ticket introuvable"));

        if (ticket.getPrix() == null || ticket.getPrix() <= 0) {
            throw new BadRequestException("Ce type de ticket n'est pas disponible à l'achat");
        }
        return ticket;
    }

    /* ==============================
       LECTURES
    ============================== */
    public OrderResponse getOrder(String userId, boolean admin, String orderId) {
        return toResponse(findAccessibleOrder(orderId, userId, admin));
    }

    public List<OrderResponse> getOrdersByUser(String userId) {
        List<Order> orders = orderRepository.findByUserId(userId);

        Set<String> eventIds = orders.stream()
                .map(Order::getEventId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, Event> events = new HashMap<>();
        eventRepository.findAllById(eventIds).forEach(e -> events.put(e.getId(), e));

        return orders.stream()
                .sorted(Comparator.comparing(Order::getDateAchat,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(o -> toResponse(o, events.get(o.getEventId())))
                .collect(Collectors.toList());
    }

    /**
     * Retourne la commande si elle appartient à l'utilisateur (ou si celui-ci est administrateur).
     * Une commande d'autrui est présentée comme introuvable pour ne pas révéler son existence.
     */
    public Order findAccessibleOrder(String orderId, String userId, boolean admin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Commande introuvable"));
        if (!admin && !Objects.equals(order.getUserId(), userId)) {
            throw new ResourceNotFoundException("Commande introuvable");
        }
        return order;
    }

    public OrderResponse toResponse(Order order) {
        Event event = order.getEventId() == null
                ? null
                : eventRepository.findById(order.getEventId()).orElse(null);
        return toResponse(order, event);
    }

    /* ==============================
       HELPERS
    ============================== */
    private OrderResponse toResponse(Order o, Event e) {
        String typeNom = o.getTicketTypeNom();
        if (typeNom == null && e != null && e.getTicketTypes() != null) {
            typeNom = e.getTicketTypes().stream()
                    .filter(t -> o.getTicketTypeId() != null && o.getTicketTypeId().equals(t.getId()))
                    .map(TicketType::getNom)
                    .findFirst()
                    .orElse(null);
        }
        Double montantInitial = o.getPrixUnitaireInitial() != null && o.getQuantite() != null
                ? o.getPrixUnitaireInitial() * o.getQuantite()
                : null;

        return OrderResponse.builder()
                .id(o.getId())
                .referenceTransaction(o.getReferenceTransaction())
                .statutPaiement(o.getStatutPaiement())
                .methodePaiement(o.getMethodePaiement())
                .montantInitial(montantInitial)
                .montantTotalPaye(o.getMontantTotalPaye())
                .montantRemise(o.getReductionAppliquee())
                .quantite(o.getQuantite())
                .eventId(o.getEventId())
                .eventTitre(e != null ? e.getTitre() : null)
                .ticketTypeNom(typeNom)
                .dateCreation(o.getDateAchat())
                .build();
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }
        return phone.replaceAll("\\s", "");
    }
}