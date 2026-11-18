package com.africevent.backend.repository;

import com.africevent.backend.model.IssuedTicket;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface IssuedTicketRepository extends MongoRepository<IssuedTicket, String> {

    IssuedTicket findByCodeQrUnique(String codeQrUnique);

    List<IssuedTicket> findByOrderId(String orderId);

    List<IssuedTicket> findByOrderIdIn(Collection<String> orderIds);

    long countByOrderId(String orderId);
}