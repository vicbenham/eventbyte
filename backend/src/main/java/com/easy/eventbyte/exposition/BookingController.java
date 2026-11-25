package com.easy.eventbyte.exposition;

import com.easy.eventbyte.dto.BookingIdResponse;
import com.easy.eventbyte.dto.CreateBookingRequest;
import com.easy.eventbyte.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Réservations de l'utilisateur connecté (authentifiées dans SecurityConfig). */
@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public BookingIdResponse create(@Valid @RequestBody CreateBookingRequest request, @AuthenticationPrincipal Jwt jwt) {
        return new BookingIdResponse(bookingService.create(jwt.getSubject(), request.items()));
    }
}
