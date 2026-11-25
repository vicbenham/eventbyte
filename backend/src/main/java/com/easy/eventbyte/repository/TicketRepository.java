package com.easy.eventbyte.repository;

import com.easy.eventbyte.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, String> {
}
