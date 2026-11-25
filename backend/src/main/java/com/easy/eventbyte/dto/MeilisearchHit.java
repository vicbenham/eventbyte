package com.easy.eventbyte.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MeilisearchHit(String id) {
}
