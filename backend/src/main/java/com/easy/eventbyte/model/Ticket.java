package com.easy.eventbyte.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
public class Ticket {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    private String name;

    private int quantity;

    private long priceInCents;

    public Ticket(String id, Event event) {
        this.id = id;
        this.event = event;
    }

    /** Places encore réservables : stock défini dans Strapi moins les places déjà réservées. */
    public int availableQuantity(long held) {
        return (int) Math.max(quantity - held, 0);
    }
}
