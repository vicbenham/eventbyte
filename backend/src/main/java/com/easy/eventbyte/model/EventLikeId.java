package com.easy.eventbyte.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Clé composite de {@link EventLike}. */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EventLikeId implements Serializable {

    private String userId;
    private String eventId;
}
