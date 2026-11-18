package com.africevent.backend.repository;

import com.africevent.backend.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {

    List<Order> findByUserId(String userId);

    List<Order> findByUserIdAndStatutPaiement(String userId, String statutPaiement);

    Optional<Order> findByReferenceTransaction(String referenceTransaction);

    List<Order> findByStatutPaiement(String statutPaiement);

    List<Order> findByStatutPaiementAndDateAchatBefore(String statutPaiement, LocalDateTime date);
}