package com.easy.eventbyte.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.BatchSize;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Événement ingéré depuis Strapi. L'id est celui de Strapi (evt_...). */
@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
public class Event {

    @Id
    private String id;

    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String coverUrl;

    // Absent du message Strapi pour l'instant
    private Instant startAt;

    @Embedded
    private Venue venue;

    private Instant updatedAt;

    // Charge les tickets de plusieurs événements en une requête (évite N+1 sur les listes)
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Ticket> tickets = new ArrayList<>();

    public Event(String id) {
        this.id = id;
    }
}
