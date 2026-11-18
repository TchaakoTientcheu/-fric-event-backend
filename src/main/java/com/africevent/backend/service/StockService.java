package com.africevent.backend.service;

import com.africevent.backend.model.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Réservation et restitution atomiques du stock. Les types de tickets étant imbriqués
 * dans l'événement, chaque opération est une mise à jour conditionnelle sur un seul
 * document : aucune transaction n'est nécessaire et aucune vente ne peut dépasser le stock.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final MongoTemplate mongoTemplate;
    private final EventStatusService eventStatusService;

    /**
     * Décrémente le stock si l'événement est ACTIF et que la quantité est disponible.
     *
     * @return false si le stock est insuffisant ou si l'événement n'est plus en vente
     */
    public boolean reserve(String eventId, String ticketTypeId, int quantite) {
        Criteria ticket = Criteria.where("quantiteDisponible").gte(quantite)
                .orOperator(identifiants(ticketTypeId));

        Query query = Query.query(Criteria.where("id").is(eventId)
                .and("statut").is("ACTIF")
                .and("ticketTypes").elemMatch(ticket));
        Update update = new Update().inc("ticketTypes.$.quantiteDisponible", -quantite);

        boolean reserved = mongoTemplate.updateFirst(query, update, Event.class).getModifiedCount() == 1;
        if (reserved) {
            eventStatusService.recomputeStatut(eventId);
        }
        return reserved;
    }

    /** Restitue le stock d'une commande non payée. */
    public void release(String eventId, String ticketTypeId, int quantite) {
        if (eventId == null || ticketTypeId == null) {
            return;
        }
        Criteria ticket = new Criteria().orOperator(identifiants(ticketTypeId));

        Query query = Query.query(Criteria.where("id").is(eventId)
                .and("ticketTypes").elemMatch(ticket));
        Update update = new Update().inc("ticketTypes.$.quantiteDisponible", quantite);

        if (mongoTemplate.updateFirst(query, update, Event.class).getModifiedCount() == 1) {
            eventStatusService.recomputeStatut(eventId);
        } else {
            log.warn("Restitution de stock impossible : événement {} ou type de ticket {} introuvable",
                    eventId, ticketTypeId);
        }
    }

    /** L'identifiant d'un objet imbriqué est stocké sous _id par Spring Data ; id est accepté par sécurité. */
    private Criteria[] identifiants(String ticketTypeId) {
        return new Criteria[]{
                Criteria.where("_id").is(ticketTypeId),
                Criteria.where("id").is(ticketTypeId)
        };
    }
}