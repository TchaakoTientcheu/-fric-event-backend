package com.africevent.backend.repository;

import com.africevent.backend.model.PaymentPromo;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PaymentPromoRepository extends MongoRepository<PaymentPromo, String> {

    List<PaymentPromo> findByEventId(String eventId);

    List<PaymentPromo> findByEventIdIsNull();

    List<PaymentPromo> findByEventIdAndMethodePaiement(String eventId, String methodePaiement);
}