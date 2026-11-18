package com.africevent.backend.service;

import com.africevent.backend.config.SmartPayProperties;
import com.africevent.backend.dto.OrderResponse;
import com.africevent.backend.dto.PaymentInitResponse;
import com.africevent.backend.dto.SmartPaySession;
import com.africevent.backend.dto.SmartPaySessionRequest;
import com.africevent.backend.dto.SmartPayWebhookEvent;
import com.africevent.backend.exception.SmartPayException;
import com.africevent.backend.model.Event;
import com.africevent.backend.model.Order;
import com.africevent.backend.model.OrderStatus;
import com.africevent.backend.model.User;
import com.africevent.backend.repository.EventRepository;
import com.africevent.backend.repository.OrderRepository;
import com.africevent.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Cycle de paiement SmartPay : création de session, confirmation (webhook ou lecture
 * serveur de la session), échec, expiration. Chaque transition de statut est une mise à
 * jour atomique conditionnelle : un événement rejoué ou concurrent n'a aucun effet double.
 *
 * Le client choisit son moyen de paiement avant de payer : le montant envoyé tient compte de la
 * remise de ce moyen, et la session est restreinte à ce seul moyen pour qu'il ne puisse pas payer
 * avec un autre moyen au tarif remisé.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPaymentService {

    private static final int SESSION_MIN_MINUTES = 5;
    private static final int SESSION_MAX_MINUTES = 1440;
    private static final int DESCRIPTION_MAX_LENGTH = 255;
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{8,15}$");

    private static final String SESSION_SUCCEEDED = "succeeded";
    private static final String SESSION_EXPIRED = "expired";
    private static final String SESSION_CANCELLED = "cancelled";
    private static final String SESSION_OPEN = "open";
    private static final String SESSION_PENDING = "pending";

    private static final String CODE_METHOD_NOT_AVAILABLE = "PAYMENT_METHOD_NOT_AVAILABLE";
    private static final String CODE_NOT_CONFIGURED = "NOT_CONFIGURED";
    private static final String CODE_IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";

    private final OrderRepository orderRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final StockService stockService;
    private final TicketGenerationService ticketGenerationService;
    private final SmartPayProperties smartPayProperties;
    private final MongoTemplate mongoTemplate;

    @Value("${app.order.expiration-minutes}")
    private int expirationMinutes;

    /* ==============================
       INITIATION DU PAIEMENT
    ============================== */
    public PaymentInitResponse initiatePayment(String userId, String orderId) {
        Order order = orderService.findAccessibleOrder(orderId, userId, false);

        if (!OrderStatus.EN_ATTENTE.equals(order.getStatutPaiement())) {
            return PaymentInitResponse.builder()
                    .orderId(order.getId())
                    .referenceTransaction(order.getReferenceTransaction())
                    .statut(order.getStatutPaiement())
                    .message("Commande déjà traitée")
                    .build();
        }

        if (order.getCheckoutUrl() != null) {
            return PaymentInitResponse.builder()
                    .orderId(order.getId())
                    .referenceTransaction(order.getReferenceTransaction())
                    .paymentUrl(order.getCheckoutUrl())
                    .statut("PENDING")
                    .message("Paiement déjà initié. Poursuivez sur la page de paiement.")
                    .build();
        }

        String titre = eventRepository.findById(order.getEventId())
                .map(Event::getTitre)
                .orElse("Événement");

        User user = userRepository.findById(userId).orElse(null);

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("orderId", order.getId());
        metadata.put("reference", order.getReferenceTransaction());

        // Corps conforme au guide : les champs absents ne sont pas envoyés.
        // Le montant est celui de la commande (remise du moyen choisi déjà appliquée par le serveur).
        SmartPaySessionRequest request = SmartPaySessionRequest.builder()
                .amount(Math.round(order.getMontantTotalPaye()))
                .currency(smartPayProperties.getCurrency())
                .merchantRef(order.getId())
                .description(truncate("Billets - " + titre + " (x" + order.getQuantite() + ")"))
                .returnUrl(blankToNull(smartPayProperties.getReturnUrl()))
                .cancelUrl(blankToNull(smartPayProperties.getCancelUrl()))
                .customer(buildCustomer(order, user))
                .locale(smartPayProperties.getLocale())
                .expiresInMinutes(sessionDuration())
                .paymentMethods(restrictedMethods(order))
                .metadata(metadata)
                .build();

        SmartPaySession session;
        try {
            session = paymentService.createSession(request);
        } catch (SmartPayException e) {
            throw toHttpException(e);
        }

        if (session.getId() == null || session.getCheckoutUrl() == null) {
            log.error("Réponse SmartPay incomplète pour la commande {}", order.getId());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Réponse invalide du service de paiement");
        }

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(order.getId())
                        .and("statutPaiement").is(OrderStatus.EN_ATTENTE)),
                new Update()
                        .set("smartPaySessionId", session.getId())
                        .set("checkoutUrl", session.getCheckoutUrl()),
                Order.class);

        return PaymentInitResponse.builder()
                .orderId(order.getId())
                .referenceTransaction(order.getReferenceTransaction())
                .paymentUrl(session.getCheckoutUrl())
                .statut("PENDING")
                .message("Redirection vers la page de paiement sécurisée")
                .build();
    }

    /* ==============================
       STATUT (retour client / polling)
    ============================== */
    public OrderResponse refreshStatus(String userId, boolean admin, String orderId) {
        Order order = orderService.findAccessibleOrder(orderId, userId, admin);

        if (OrderStatus.EN_ATTENTE.equals(order.getStatutPaiement()) && order.getSmartPaySessionId() != null) {
            try {
                applySession(order, paymentService.getSession(order.getSmartPaySessionId()));
            } catch (SmartPayException e) {
                log.warn("Vérification SmartPay impossible pour la commande {} : {}", order.getId(), e.getMessage());
            }
            order = orderRepository.findById(orderId).orElse(order);
        }

        if (OrderStatus.PAYE.equals(order.getStatutPaiement()) && !Boolean.TRUE.equals(order.getBilletsGeneres())) {
            ticketGenerationService.generateTickets(order);
        }
        return orderService.toResponse(order);
    }

    /* ==============================
       ANNULATION PAR LE CLIENT
    ============================== */

    /**
     * Le client revient en arrière (autre moyen de paiement, abandon) : la session SmartPay encore
     * ouverte est annulée et le stock est restitué. Refusé si un paiement est en cours de validation.
     */
    public OrderResponse cancelOrder(String userId, String orderId) {
        Order order = orderService.findAccessibleOrder(orderId, userId, false);

        if (!OrderStatus.EN_ATTENTE.equals(order.getStatutPaiement())) {
            return orderService.toResponse(order);
        }

        String sessionId = order.getSmartPaySessionId();
        if (sessionId == null) {
            failOrder(order);
        } else {
            SmartPaySession session;
            try {
                session = paymentService.getSession(sessionId);
            } catch (SmartPayException e) {
                throw toHttpException(e);
            }

            String status = session.getStatus();
            if (SESSION_PENDING.equals(status)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Un paiement est en cours de validation. Veuillez patienter quelques instants.");
            }
            if (SESSION_OPEN.equals(status)) {
                try {
                    paymentService.cancelSession(sessionId);
                } catch (SmartPayException e) {
                    if (e.getHttpStatus() == 409) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Un paiement est en cours ou déjà réglé pour cette commande.");
                    }
                    if (e.getHttpStatus() != 410) {
                        throw toHttpException(e);
                    }
                }
                failOrder(order);
            } else {
                applySession(order, session);
            }
        }

        return orderService.toResponse(orderRepository.findById(orderId).orElse(order));
    }

    /** Libère le stock d'une commande pour laquelle aucune session de paiement n'a pu être créée. */
    public void abandonUnstartedOrder(String orderId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            if (OrderStatus.EN_ATTENTE.equals(order.getStatutPaiement()) && order.getSmartPaySessionId() == null) {
                failOrder(order);
            }
        });
    }

    /* ==============================
       WEBHOOK
    ============================== */
    public void handleWebhook(SmartPayWebhookEvent event) {
        if (event == null || event.getType() == null || event.getData() == null
                || event.getData().getSession() == null) {
            return;
        }
        SmartPaySession session = event.getData().getSession();
        if (session.getMerchantRef() == null) {
            return;
        }

        Order order = orderRepository.findById(session.getMerchantRef()).orElse(null);
        if (order == null) {
            log.warn("Webhook SmartPay {} pour une commande inconnue", event.getId());
            return;
        }

        switch (event.getType()) {
            case "payment.succeeded":
            case "session.expired":
            case "session.cancelled":
                applySession(order, session);
                break;
            case "payment.duplicate":
                log.error("Paiement en double signalé par SmartPay : commande {} session {}",
                        order.getId(), session.getId());
                flagAnomaly(order, "PAIEMENT_EN_DOUBLE");
                break;
            default:
                log.info("Événement SmartPay {} ignoré pour la commande {}", event.getType(), order.getId());
        }
    }

    /* ==============================
       EXPIRATION DES COMMANDES NON PAYÉES
    ============================== */
    public void processStaleOrders() {
        LocalDateTime limit = LocalDateTime.now().minusMinutes(expirationMinutes);
        List<Order> stale = orderRepository.findByStatutPaiementAndDateAchatBefore(OrderStatus.EN_ATTENTE, limit);

        for (Order order : stale) {
            try {
                expireOrder(order);
            } catch (SmartPayException e) {
                log.warn("Expiration reportée pour la commande {} : {}", order.getId(), e.getMessage());
            } catch (RuntimeException e) {
                log.error("Échec du traitement de la commande expirée {}", order.getId(), e);
            }
        }
    }

    private void expireOrder(Order order) {
        String sessionId = order.getSmartPaySessionId();
        if (sessionId == null) {
            failOrder(order);
            return;
        }

        SmartPaySession session = paymentService.getSession(sessionId);
        String status = session.getStatus();

        if (SESSION_PENDING.equals(status)) {
            // Paiement Mobile Money en cours de validation : SmartPay attend l'opérateur.
            return;
        }
        if (SESSION_OPEN.equals(status)) {
            try {
                paymentService.cancelSession(sessionId);
            } catch (SmartPayException e) {
                if (e.getHttpStatus() == 410) {
                    failOrder(order);
                    return;
                }
                if (e.getHttpStatus() == 409) {
                    log.info("Commande {} : paiement en cours ou déjà réglé, nouvelle vérification au prochain passage",
                            order.getId());
                    return;
                }
                throw e;
            }
            failOrder(order);
            return;
        }
        applySession(order, session);
    }

    /* ==============================
       TRANSITIONS
    ============================== */
    private void applySession(Order order, SmartPaySession session) {
        if (session == null || session.getStatus() == null) {
            return;
        }
        if (!order.getId().equals(session.getMerchantRef())) {
            log.error("Session SmartPay {} incohérente avec la commande {}", session.getId(), order.getId());
            return;
        }
        if (order.getSmartPaySessionId() != null && !order.getSmartPaySessionId().equals(session.getId())) {
            log.error("Session SmartPay {} différente de celle enregistrée pour la commande {}",
                    session.getId(), order.getId());
            return;
        }

        switch (session.getStatus()) {
            case SESSION_SUCCEEDED:
                confirmSuccess(order, session);
                break;
            case SESSION_EXPIRED:
            case SESSION_CANCELLED:
                failOrder(order);
                break;
            default:
                // open ou pending : aucun changement
                break;
        }
    }

    private void confirmSuccess(Order order, SmartPaySession session) {
        Order current = orderRepository.findById(order.getId()).orElse(order);

        if (OrderStatus.ECHEC.equals(current.getStatutPaiement())) {
            log.error("Paiement reçu pour la commande {} déjà en échec (session {})",
                    current.getId(), session.getId());
            flagAnomaly(current, "PAIEMENT_RECU_COMMANDE_ECHEC");
            return;
        }

        if (OrderStatus.EN_ATTENTE.equals(current.getStatutPaiement())) {
            if (!isAmountConsistent(current, session)) {
                log.error("Montant ou devise incohérent pour la commande {} (session {})",
                        current.getId(), session.getId());
                flagAnomaly(current, "MONTANT_INCOHERENT");
                return;
            }
            if (markPaid(current, session)) {
                flagDiscrepancies(current, session);
            }
            current = orderRepository.findById(current.getId()).orElse(current);
        }

        if (OrderStatus.PAYE.equals(current.getStatutPaiement())) {
            ticketGenerationService.generateTickets(current);
        }
    }

    /**
     * Le montant de la session est celui demandé par ce serveur, remise du moyen choisi déjà comprise.
     */
    private boolean isAmountConsistent(Order order, SmartPaySession session) {
        return session.getAmount() != null
                && order.getMontantTotalPaye() != null
                && Math.round(session.getAmount()) == Math.round(order.getMontantTotalPaye())
                && smartPayProperties.getCurrency().equalsIgnoreCase(session.getCurrency());
    }

    /**
     * Le paiement est acquis et n'est pas remis en cause, mais un écart (autre moyen que celui choisi,
     * montant encaissé différent par exemple à cause d'une remise configurée aussi chez SmartPay)
     * est signalé sur la commande pour le rapprochement comptable.
     */
    private void flagDiscrepancies(Order order, SmartPaySession session) {
        String paidWith = session.getPaidWith();
        if (order.getMethodePaiement() != null && paidWith != null
                && !order.getMethodePaiement().equalsIgnoreCase(paidWith)) {
            log.error("Commande {} : moyen payé {} différent du moyen choisi", order.getId(), paidWith);
            flagAnomaly(order, "MOYEN_PAIEMENT_DIFFERENT");
            return;
        }
        if (session.getPaidAmount() != null && order.getMontantTotalPaye() != null
                && Math.round(session.getPaidAmount()) != Math.round(order.getMontantTotalPaye())) {
            log.error("Commande {} : montant encaissé différent du montant demandé", order.getId());
            flagAnomaly(order, "MONTANT_ENCAISSE_DIFFERENT");
        }
    }

    private boolean markPaid(Order order, SmartPaySession session) {
        Query query = Query.query(Criteria.where("id").is(order.getId())
                .and("statutPaiement").is(OrderStatus.EN_ATTENTE));
        Update update = new Update()
                .set("statutPaiement", OrderStatus.PAYE)
                .set("datePaiement", LocalDateTime.now())
                .set("moyenPaiementUtilise", session.getPaidWith())
                .set("transactionNo", session.getTransactionNo())
                .set("montantEncaisse", session.getPaidAmount())
                .set("smartPaySessionId", session.getId());
        // Le moyen choisi par le client reste la référence ; il n'est renseigné ici que pour une ancienne commande.
        if (order.getMethodePaiement() == null) {
            update.set("methodePaiement", session.getPaidWith());
        }
        return mongoTemplate.updateFirst(query, update, Order.class).getModifiedCount() == 1;
    }

    /** Passe la commande en échec et restitue le stock : seul l'appel qui change le statut libère le stock. */
    private boolean failOrder(Order order) {
        Query query = Query.query(Criteria.where("id").is(order.getId())
                .and("statutPaiement").is(OrderStatus.EN_ATTENTE));
        Update update = Update.update("statutPaiement", OrderStatus.ECHEC);

        boolean modified = mongoTemplate.updateFirst(query, update, Order.class).getModifiedCount() == 1;
        if (modified) {
            stockService.release(order.getEventId(), order.getTicketTypeId(), order.getQuantite());
        }
        return modified;
    }

    private void flagAnomaly(Order order, String code) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(order.getId())),
                Update.update("anomalie", code),
                Order.class);
    }

    /* ==============================
       HELPERS
    ============================== */

    /** Seul le moyen choisi est proposé sur la page SmartPay : la remise ne peut pas être détournée. */
    private List<String> restrictedMethods(Order order) {
        String method = blankToNull(order.getMethodePaiement());
        return method == null ? null : Collections.singletonList(method);
    }

    /**
     * Traduit une erreur SmartPay en réponse HTTP exploitable par le client.
     * Le détail technique (code, requestId) reste dans les journaux écrits par PaymentService.
     */
    private ResponseStatusException toHttpException(SmartPayException e) {
        String code = e.getCode();

        if (CODE_METHOD_NOT_AVAILABLE.equals(code)) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce moyen de paiement n'est pas disponible pour le moment");
        }
        // Identifiants absents : réessayer ne sert à rien tant que la configuration n'est pas corrigée.
        if (CODE_NOT_CONFIGURED.equals(code)) {
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Le paiement en ligne n'est pas disponible actuellement. Veuillez contacter le support.");
        }
        if (CODE_IDEMPOTENCY_CONFLICT.equals(code)) {
            return new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un paiement est déjà en cours pour cette commande. Veuillez créer une nouvelle commande.");
        }
        if (e.isTemporary()) {
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Le service de paiement est momentanément indisponible. Veuillez réessayer dans quelques instants.");
        }
        // Refus de SmartPay (domaine de retour, clé, signature...) : problème de configuration marchand.
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Le paiement n'a pas pu être initialisé. Veuillez réessayer ou contacter le support.");
    }

    /** Coordonnées du client : email du compte et téléphone valide ; null si aucune n'est disponible. */
    private SmartPaySessionRequest.Customer buildCustomer(Order order, User user) {
        String email = user != null ? blankToNull(user.getEmail()) : null;
        String phone = resolvePhone(order, user);
        if (email == null && phone == null) {
            return null;
        }
        return SmartPaySessionRequest.Customer.builder().email(email).phone(phone).build();
    }

    private String resolvePhone(Order order, User user) {
        String candidate = order.getTelephoneClient();
        if (candidate == null || candidate.trim().isEmpty()) {
            candidate = user != null ? user.getTelephone() : null;
        }
        if (candidate == null) {
            return null;
        }
        String normalized = candidate.replaceAll("\\s", "");
        return PHONE_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    private int sessionDuration() {
        return Math.max(SESSION_MIN_MINUTES, Math.min(SESSION_MAX_MINUTES, expirationMinutes));
    }

    private String truncate(String value) {
        return value.length() <= DESCRIPTION_MAX_LENGTH ? value : value.substring(0, DESCRIPTION_MAX_LENGTH);
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}