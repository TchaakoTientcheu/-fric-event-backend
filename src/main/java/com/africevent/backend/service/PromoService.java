package com.africevent.backend.service;

import com.africevent.backend.model.PaymentPromo;
import com.africevent.backend.repository.PaymentPromoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PromoService {

    private final PaymentPromoRepository promoRepository;

    /**
     * Pourcentage applicable : promotion active spécifique à l'événement, sinon promotion
     * globale active, sinon 0. Même règle de priorité que PaymentPromoController.
     */
    public double tauxReduction(String eventId, String methodePaiement) {
        if (methodePaiement == null) {
            return 0.0;
        }
        Double specifique = trouver(promoRepository.findByEventId(eventId), methodePaiement);
        if (specifique != null) {
            return borner(specifique);
        }
        Double globale = trouver(promoRepository.findByEventIdIsNull(), methodePaiement);
        return globale != null ? borner(globale) : 0.0;
    }

    private Double trouver(List<PaymentPromo> promos, String methodePaiement) {
        return promos.stream()
                .filter(p -> Boolean.TRUE.equals(p.getActif()))
                .filter(p -> methodePaiement.equalsIgnoreCase(p.getMethodePaiement()))
                .map(PaymentPromo::getPourcentageReduction)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private double borner(double pourcentage) {
        return Math.max(0.0, Math.min(100.0, pourcentage));
    }
}