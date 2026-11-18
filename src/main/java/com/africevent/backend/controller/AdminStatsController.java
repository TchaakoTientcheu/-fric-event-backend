package com.africevent.backend.controller;

import com.africevent.backend.dto.EventStatsResponse;
import com.africevent.backend.service.AdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class AdminStatsController {

    private final AdminStatsService statsService;

    @GetMapping
    public ResponseEntity<EventStatsResponse> getStats() {
        return ResponseEntity.ok(statsService.getStats());
    }
}