package com.easy.eventbyte.exposition;

import com.easy.eventbyte.dto.EventResponse;
import com.easy.eventbyte.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

    /** GET /events?ids=a,b,c : Spring découpe la liste sur les virgules. */
    @GetMapping(params = "ids")
    public List<EventResponse> getByIds(@RequestParam List<String> ids) {
        return eventService.getByIds(ids);
    }

    @GetMapping("/discover")
    public List<EventResponse> discover(@RequestParam double lat, @RequestParam double lng) {
        return eventService.discover(lat, lng);
    }

    @GetMapping("/search")
    public List<EventResponse> search(@RequestParam double lat,
                                      @RequestParam double lng,
                                      @RequestParam double radius,
                                      @RequestParam(defaultValue = "") String query) {
        return eventService.search(query, lat, lng, radius);
    }

    /** Authentifié (SecurityConfig) : uniquement les likes de l'utilisateur du token. */
    @GetMapping("/liked")
    public List<EventResponse> liked(@AuthenticationPrincipal Jwt jwt) {
        return eventService.getLiked(jwt.getSubject());
    }

    @GetMapping("/{id}")
    public EventResponse getById(@PathVariable String id) {
        return eventService.getById(id);
    }
}
