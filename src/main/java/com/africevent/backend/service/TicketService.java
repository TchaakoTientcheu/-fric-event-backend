package com.africevent.backend.service;

import com.africevent.backend.dto.TicketResponse;
import com.africevent.backend.exception.ResourceNotFoundException;
import com.africevent.backend.model.Event;
import com.africevent.backend.model.IssuedTicket;
import com.africevent.backend.model.Order;
import com.africevent.backend.model.OrderStatus;
import com.africevent.backend.model.TicketType;
import com.africevent.backend.model.User;
import com.africevent.backend.repository.EventRepository;
import com.africevent.backend.repository.IssuedTicketRepository;
import com.africevent.backend.repository.OrderRepository;
import com.africevent.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketService {

    private static final String TYPE_PAR_DEFAUT = "Billet";

    private final IssuedTicketRepository ticketRepository;
    private final OrderRepository orderRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final QrCodeService qrCodeService;
    private final OrderService orderService;
    private final TicketGenerationService ticketGenerationService;

    public List<TicketResponse> getTicketsByOrder(String userId, boolean admin, String orderId) {
        Order order = orderService.findAccessibleOrder(orderId, userId, admin);
        if (!OrderStatus.PAYE.equals(order.getStatutPaiement())) {
            return Collections.emptyList();
        }
        if (!Boolean.TRUE.equals(order.getBilletsGeneres())) {
            ticketGenerationService.generateTickets(order);
        }
        return toResponses(ticketRepository.findByOrderId(order.getId()), Collections.singletonList(order));
    }

    public List<TicketResponse> getMyTickets(String userId) {
        List<Order> paidOrders = orderRepository.findByUserIdAndStatutPaiement(userId, OrderStatus.PAYE);
        if (paidOrders.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> orderIds = paidOrders.stream().map(Order::getId).collect(Collectors.toList());
        return toResponses(ticketRepository.findByOrderIdIn(orderIds), paidOrders);
    }

    public TicketResponse getTicket(String userId, boolean admin, String ticketId) {
        IssuedTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Billet introuvable"));
        Order order = orderRepository.findById(ticket.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Billet introuvable"));
        if (!admin && !Objects.equals(order.getUserId(), userId)) {
            throw new ResourceNotFoundException("Billet introuvable");
        }
        return toResponses(Collections.singletonList(ticket), Collections.singletonList(order)).get(0);
    }

    /* ==============================
       MAPPING (chargements groupés, sans requête par billet)
    ============================== */
    private List<TicketResponse> toResponses(List<IssuedTicket> tickets, List<Order> orders) {
        Map<String, Order> ordersById = orders.stream()
                .collect(Collectors.toMap(Order::getId, o -> o));

        Map<String, Event> events = new HashMap<>();
        eventRepository.findAllById(distinct(orders, Order::getEventId))
                .forEach(e -> events.put(e.getId(), e));

        Map<String, User> users = new HashMap<>();
        userRepository.findAllById(distinct(orders, Order::getUserId))
                .forEach(u -> users.put(u.getId(), u));

        return tickets.stream()
                .filter(t -> ordersById.containsKey(t.getOrderId()))
                .sorted(Comparator.comparing(IssuedTicket::getDateGeneration,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(t -> toResponse(t, ordersById.get(t.getOrderId()), events, users))
                .collect(Collectors.toList());
    }

    private TicketResponse toResponse(IssuedTicket t, Order order, Map<String, Event> events, Map<String, User> users) {
        Event event = events.get(order.getEventId());
        User user = users.get(order.getUserId());

        return TicketResponse.builder()
                .id(t.getId())
                .orderId(order.getId())
                .eventId(order.getEventId())
                .codeQrUnique(t.getCodeQrUnique())
                .qrImageUrl(qrCodeService.toDataUri(t.getCodeQrUnique()))
                .estUtilise(Boolean.TRUE.equals(t.getEstUtilise()))
                .dateGeneration(t.getDateGeneration())
                .eventTitre(event != null ? event.getTitre() : null)
                .eventLieu(event != null ? event.getLieu() : null)
                .eventDate(event != null ? event.getDateEvent() : null)
                .eventImageUrl(event != null ? event.getImageUrl() : null)
                .ticketTypeNom(resolveTypeNom(order, event))
                .referenceCommande(order.getReferenceTransaction())
                .titulaireNom(user != null ? user.getNom() : null)
                .build();
    }

    private String resolveTypeNom(Order order, Event event) {
        if (order.getTicketTypeNom() != null) {
            return order.getTicketTypeNom();
        }
        if (event != null && event.getTicketTypes() != null) {
            return event.getTicketTypes().stream()
                    .filter(t -> order.getTicketTypeId() != null && order.getTicketTypeId().equals(t.getId()))
                    .map(TicketType::getNom)
                    .findFirst()
                    .orElse(TYPE_PAR_DEFAUT);
        }
        return TYPE_PAR_DEFAUT;
    }

    private Set<String> distinct(List<Order> orders, Function<Order, String> extractor) {
        return orders.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}