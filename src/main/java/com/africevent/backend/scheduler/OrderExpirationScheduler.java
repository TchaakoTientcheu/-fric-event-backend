package com.africevent.backend.scheduler;

import com.africevent.backend.service.OrderPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private final OrderPaymentService orderPaymentService;

    @Scheduled(
            fixedDelayString = "${app.order.expiration-check-ms}",
            initialDelayString = "${app.order.expiration-check-ms}")
    public void expireStaleOrders() {
        orderPaymentService.processStaleOrders();
    }
}