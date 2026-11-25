package com.easy.eventbyte.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Active les tâches planifiées (@Scheduled), comme l'expiration des réservations. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
