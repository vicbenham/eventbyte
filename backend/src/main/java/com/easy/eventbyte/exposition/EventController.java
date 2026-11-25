package com.easy.eventbyte.exposition;

import com.easy.eventbyte.dto.EventResponse;
import com.easy.eventbyte.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping("/discover")
    public List<EventResponse> discover(@RequestParam double lat, @RequestParam double lng) {
        return eventService.discover(lat, lng);
    }

    @GetMapping("/{id}")
    public EventResponse getById(@PathVariable String id) {
        return eventService.getById(id);
    }
}
