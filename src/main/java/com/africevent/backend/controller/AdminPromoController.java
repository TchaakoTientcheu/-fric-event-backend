package com.africevent.backend.controller;

import com.africevent.backend.model.PaymentPromo;
import com.africevent.backend.repository.PaymentPromoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/promos")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class AdminPromoController {

    private final PaymentPromoRepository promoRepository;

    @GetMapping
    public List<PaymentPromo> getAll() {
        return promoRepository.findAll();
    }

    @GetMapping("/event/{eventId}")
    public List<PaymentPromo> getByEvent(@PathVariable String eventId) {
        return promoRepository.findByEventId(eventId);
    }

    @PostMapping
    public PaymentPromo create(@RequestBody PaymentPromo promo) {
        if (promo.getActif() == null) {
            promo.setActif(true);
        }
        return promoRepository.save(promo);
    }

    @PutMapping("/{id}")
    public PaymentPromo update(@PathVariable String id, @RequestBody PaymentPromo promo) {
        promo.setId(id);
        return promoRepository.save(promo);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        if (!promoRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        promoRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Promo supprimée"));
    }
}