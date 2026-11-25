package com.easy.eventbyte.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/** Champs d'un événement lus via l'API REST Strapi (absents du message RabbitMQ). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiEventData(Instant startAt) {
}
