package com.africevent.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Active @Async : l'envoi des billets par email ne doit pas retarder la reponse au webhook SmartPay. */
@Configuration
@EnableAsync
public class AsyncConfig {
}