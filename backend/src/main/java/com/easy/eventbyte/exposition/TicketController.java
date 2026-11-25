package com.easy.eventbyte.exposition;

import com.easy.eventbyte.dto.TicketResponse;
import com.easy.eventbyte.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping("/events/{eventId}/tickets")
    public List<TicketResponse> getTickets(@PathVariable String eventId) {
        return ticketService.getTickets(eventId);
    }
}
