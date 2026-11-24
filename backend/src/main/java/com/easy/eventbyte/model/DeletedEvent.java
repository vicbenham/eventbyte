package com.easy.eventbyte.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "deleted_events")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeletedEvent {

    @Id
    private String id;

    private Instant deletedAt;
}
