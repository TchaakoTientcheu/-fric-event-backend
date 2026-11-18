package com.africevent.backend.repository;

import com.africevent.backend.model.Event;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface EventRepository extends MongoRepository<Event, String> {

    List<Event> findByStatut(String statut);
}