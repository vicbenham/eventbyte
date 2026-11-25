package com.easy.eventbyte.exposition;

import com.easy.eventbyte.dto.BookingIdResponse;
import com.easy.eventbyte.dto.BookingResponse;
import com.easy.eventbyte.dto.CreateBookingRequest;
import com.easy.eventbyte.service.BookingService;
import com.easy.eventbyte.service.BookingStreamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/** Réservations de l'utilisateur connecté (authentifiées dans SecurityConfig). */
@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final BookingStreamService bookingStreamService;

    @PostMapping
    public BookingIdResponse create(@Valid @RequestBody CreateBookingRequest request, @AuthenticationPrincipal Jwt jwt) {
        return new BookingIdResponse(bookingService.create(jwt.getSubject(), request.items()));
    }

    @GetMapping("/user")
    public List<BookingResponse> userBookings(@AuthenticationPrincipal Jwt jwt) {
        return bookingService.getUserBookings(jwt.getSubject());
    }

    /** Flux SSE : événement booking_updated à chaque changement de statut d'une réservation de l'utilisateur. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt) {
        return bookingStreamService.open(jwt.getSubject());
    }

    @GetMapping("/{id}")
    public BookingResponse getById(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return bookingService.getById(jwt.getSubject(), id);
    }
}
