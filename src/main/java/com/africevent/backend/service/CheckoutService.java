package com.africevent.backend.service;

import com.africevent.backend.config.PaymentMethodProperties;
import com.africevent.backend.config.SmartPayProperties;
import com.africevent.backend.dto.AuthResponse;
import com.africevent.backend.dto.CheckoutStartRequest;
import com.africevent.backend.dto.CheckoutStartResponse;
import com.africevent.backend.dto.CreateOrderRequest;
import com.africevent.backend.dto.OrderResponse;
import com.africevent.backend.dto.PaymentInitResponse;
import com.africevent.backend.dto.QuoteRequest;
import com.africevent.backend.dto.QuoteResponse;
import com.africevent.backend.exception.ConflictException;
import com.africevent.backend.model.Event;
import com.africevent.backend.model.TicketType;
import com.africevent.backend.model.User;
import com.africevent.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Achat sans compte prealable : devis par moyen de paiement, puis creation en une operation
 * du compte (email verifie par code), de la commande au prix remise et de la session SmartPay.
 */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private static final String MSG_STOCK = "Stock insuffisant pour ce type de ticket";

    private final OrderService orderService;
    private final OrderPaymentService orderPaymentService;
    private final OtpAccountService otpAccountService;
    private final PricingService pricingService;
    private final PaymentMethodProperties paymentMethods;
    private final SmartPayProperties smartPayProperties;
    private final JwtUtil jwtUtil;

    /* ==============================
       DEVIS
    ============================== */
    public QuoteResponse quote(QuoteRequest req) {
        TicketType ticket = validatedTicket(req.getEventId(), req.getTicketTypeId(), req.getQuantite());
        Event event = orderService.loadPurchasableEvent(req.getEventId());

        PricingService.Pricing base = pricingService.compute(
                event.getId(), ticket.getPrix(), req.getQuantite(), null);

        List<QuoteResponse.Option> options = paymentMethods.getMethods().entrySet().stream()
                .map(entry -> {
                    PricingService.Pricing pricing = pricingService.compute(
                            event.getId(), ticket.getPrix(), req.getQuantite(), entry.getKey());
                    return QuoteResponse.Option.builder()
                            .code(entry.getKey())
                            .libelle(entry.getValue())
                            .tauxReduction(pricing.getTaux())
                            .montantRemise(pricing.getMontantRemise().longValue())
                            .montantFinal(pricing.getMontantFinal().longValue())
                            .build();
                })
                .collect(Collectors.toList());

        return QuoteResponse.builder()
                .eventId(event.getId())
                .eventTitre(event.getTitre())
                .ticketTypeId(ticket.getId())
                .ticketTypeNom(ticket.getNom())
                .quantite(req.getQuantite())
                .prixUnitaire(ticket.getPrix())
                .montantInitial(base.getMontantInitial().longValue())
                .devise(smartPayProperties.getCurrency())
                .options(options)
                .build();
    }

    /* ==============================
       DEMARRAGE DU PAIEMENT
    ============================== */
    public CheckoutStartResponse start(CheckoutStartRequest req) {
        // Tout ce qui peut etre refuse est controle avant de consommer le code de l'email.
        String methode = orderService.requireSupportedMethod(req.getMethodePaiement());
        validatedTicket(req.getEventId(), req.getTicketTypeId(), req.getQuantite());

        User user = otpAccountService.authenticate(req.getEmail(), req.getCode());

        CreateOrderRequest orderRequest = new CreateOrderRequest();
        orderRequest.setEventId(req.getEventId());
        orderRequest.setTicketTypeId(req.getTicketTypeId());
        orderRequest.setQuantite(req.getQuantite());
        orderRequest.setMethodePaiement(methode);
        orderRequest.setPhoneNumber(req.getPhoneNumber());

        OrderResponse order = orderService.createOrder(user.getId(), orderRequest);

        PaymentInitResponse payment;
        try {
            payment = orderPaymentService.initiatePayment(user.getId(), order.getId());
        } catch (RuntimeException e) {
            // Aucune session de paiement : on libere tout de suite le stock reserve.
            orderPaymentService.abandonUnstartedOrder(order.getId());
            throw e;
        }

        return CheckoutStartResponse.builder()
                .auth(toAuth(user))
                .payment(payment)
                .build();
    }

    public AuthResponse toAuth(User user) {
        return AuthResponse.builder()
                .id(user.getId())
                .token(jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole()))
                .role(user.getRole())
                .nom(user.getNom())
                .email(user.getEmail())
                .build();
    }

    private TicketType validatedTicket(String eventId, String ticketTypeId, int quantite) {
        orderService.checkQuantity(quantite);
        Event event = orderService.loadPurchasableEvent(eventId);
        TicketType ticket = orderService.findPurchasableTicket(event, ticketTypeId);
        if (ticket.getQuantiteDisponible() == null || ticket.getQuantiteDisponible() < quantite) {
            throw new ConflictException(MSG_STOCK);
        }
        return ticket;
    }
}