package com.africevent.backend.service;

import com.africevent.backend.dto.EventStatsResponse;
import com.africevent.backend.model.Event;
import com.africevent.backend.model.Order;
import com.africevent.backend.repository.EventRepository;
import com.africevent.backend.repository.OrderRepository;
import com.africevent.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final EventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public EventStatsResponse getStats() {
        List<Event> events = eventRepository.findAll();
        List<Order> orders = orderRepository.findAll();

        long actifs = events.stream().filter(e -> "ACTIF".equals(e.getStatut())).count();
        long termines = events.stream().filter(e -> "TERMINE".equals(e.getStatut())).count();
        long annules = events.stream().filter(e -> "ANNULE".equals(e.getStatut())).count();

        long stockTotal = events.stream()
                .flatMap(e -> e.getTicketTypes().stream())
                .mapToLong(t -> t.getQuantiteDisponible() == null ? 0 : t.getQuantiteDisponible())
                .sum();

        List<Order> payees = orders.stream()
                .filter(o -> "PAYE".equals(o.getStatutPaiement()))
                .collect(Collectors.toList());

        long ticketsVendus = payees.stream()
                .mapToLong(o -> o.getQuantite() == null ? 0 : o.getQuantite())
                .sum();

        double chiffreAffaires = payees.stream()
                .mapToDouble(o -> o.getMontantTotalPaye() == null ? 0 : o.getMontantTotalPaye())
                .sum();

        Map<String, List<Order>> parMethode = payees.stream()
                .filter(o -> o.getMethodePaiement() != null)
                .collect(Collectors.groupingBy(Order::getMethodePaiement));

        List<EventStatsResponse.PaymentMethodStat> repartition = new ArrayList<>();
        for (Map.Entry<String, List<Order>> entry : parMethode.entrySet()) {
            double montant = entry.getValue().stream()
                    .mapToDouble(o -> o.getMontantTotalPaye() == null ? 0 : o.getMontantTotalPaye())
                    .sum();
            repartition.add(EventStatsResponse.PaymentMethodStat.builder()
                    .methode(entry.getKey())
                    .nombreCommandes(entry.getValue().size())
                    .montantTotal(montant)
                    .build());
        }
        repartition.sort(Comparator.comparingDouble(EventStatsResponse.PaymentMethodStat::getMontantTotal).reversed());

        Map<String, List<Order>> parEvent = payees.stream()
                .filter(o -> o.getEventId() != null)
                .collect(Collectors.groupingBy(Order::getEventId));

        Map<String, Event> eventsById = events.stream()
                .collect(Collectors.toMap(Event::getId, e -> e));

        List<EventStatsResponse.EventTopStat> topEvents = new ArrayList<>();
        for (Map.Entry<String, List<Order>> entry : parEvent.entrySet()) {
            Event ev = eventsById.get(entry.getKey());
            long vendus = entry.getValue().stream()
                    .mapToLong(o -> o.getQuantite() == null ? 0 : o.getQuantite())
                    .sum();
            double ca = entry.getValue().stream()
                    .mapToDouble(o -> o.getMontantTotalPaye() == null ? 0 : o.getMontantTotalPaye())
                    .sum();
            topEvents.add(EventStatsResponse.EventTopStat.builder()
                    .eventId(entry.getKey())
                    .titre(ev != null ? ev.getTitre() : "(supprimé)")
                    .ticketsVendus(vendus)
                    .chiffreAffaires(ca)
                    .build());
        }
        topEvents.sort(Comparator.comparingDouble(EventStatsResponse.EventTopStat::getChiffreAffaires).reversed());
        if (topEvents.size() > 5) {
            topEvents = topEvents.subList(0, 5);
        }

        return EventStatsResponse.builder()
                .totalEvenements(events.size())
                .totalEvenementsActifs(actifs)
                .totalEvenementsTermines(termines)
                .totalEvenementsAnnules(annules)
                .totalTicketsDisponibles(stockTotal)
                .totalTicketsVendus(ticketsVendus)
                .chiffreAffairesTotal(chiffreAffaires)
                .totalCommandes(orders.size())
                .totalUtilisateurs(userRepository.count())
                .repartitionParMethode(repartition)
                .topEvenements(topEvents)
                .build();
    }
}