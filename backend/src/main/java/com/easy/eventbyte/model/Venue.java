package com.easy.eventbyte.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Venue {

    @Column(name = "venue_name")
    private String name;

    @Column(name = "venue_address", length = 500)
    private String address;

    @Column(name = "venue_latitude")
    private double latitude;

    @Column(name = "venue_longitude")
    private double longitude;
}
