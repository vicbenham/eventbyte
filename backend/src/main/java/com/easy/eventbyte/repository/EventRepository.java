package com.easy.eventbyte.repository;

import com.easy.eventbyte.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, String> {
}
