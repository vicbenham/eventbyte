package com.easy.eventbyte.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Like d'un utilisateur (sub du JWT) sur un événement. */
@Entity
@Table(name = "event_likes")
@IdClass(EventLikeId.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EventLike {

    @Id
    private String userId;

    @Id
    private String eventId;

    private Instant likedAt;
}
