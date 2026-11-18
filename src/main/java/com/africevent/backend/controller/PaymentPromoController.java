package com.africevent.backend.controller;

import com.africevent.backend.model.PaymentPromo;
import com.africevent.backend.repository.PaymentPromoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/promos")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class PaymentPromoController {

    private final PaymentPromoRepository promoRepository;

    @GetMapping("/event/{eventId}")
    public List<PaymentPromo> getPromosForEvent(@PathVariable String eventId) {
        List<PaymentPromo> globales = promoRepository.findByEventIdIsNull();
        List<PaymentPromo> specifiques = promoRepository.findByEventId(eventId);

        List<PaymentPromo> activesGlobales = new ArrayList<>();
        for (PaymentPromo p : globales) {
            if (Boolean.TRUE.equals(p.getActif())) {
                activesGlobales.add(p);
            }
        }

        List<PaymentPromo> activesSpecifiques = new ArrayList<>();
        for (PaymentPromo p : specifiques) {
            if (Boolean.TRUE.equals(p.getActif())) {
                activesSpecifiques.add(p);
            }
        }

        return merge(activesGlobales, activesSpecifiques);
    }

    private List<PaymentPromo> merge(List<PaymentPromo> globales, List<PaymentPromo> specifiques) {
        Map<String, PaymentPromo> parMethode = new LinkedHashMap<>();
        for (PaymentPromo g : globales) {
            parMethode.put(g.getMethodePaiement(), g);
        }
        for (PaymentPromo s : specifiques) {
            parMethode.put(s.getMethodePaiement(), s);
        }
        return new ArrayList<>(parMethode.values());
    }
}