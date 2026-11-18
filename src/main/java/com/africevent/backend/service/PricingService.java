package com.africevent.backend.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calcul unique du prix, utilise par le devis et par la creation de commande :
 * le client voit exactement le montant que le serveur enverra a SmartPay.
 * Montants en XAF, sans decimales.
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PromoService promoService;

    @Getter
    @RequiredArgsConstructor
    public static final class Pricing {
        private final BigDecimal montantInitial;
        private final BigDecimal montantRemise;
        private final BigDecimal montantFinal;
        private final double taux;
    }

    /** methodePaiement peut etre null : aucune remise n'est alors appliquee. */
    public Pricing compute(String eventId, double prixUnitaire, int quantite, String methodePaiement) {
        BigDecimal initial = BigDecimal.valueOf(prixUnitaire)
                .multiply(BigDecimal.valueOf(quantite))
                .setScale(0, RoundingMode.HALF_UP);

        double taux = promoService.tauxReduction(eventId, methodePaiement);
        BigDecimal remise = initial.multiply(BigDecimal.valueOf(taux))
                .divide(HUNDRED, 0, RoundingMode.HALF_UP);

        return new Pricing(initial, remise, initial.subtract(remise), taux);
    }
}