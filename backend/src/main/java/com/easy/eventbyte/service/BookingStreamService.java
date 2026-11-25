package com.easy.eventbyte.service;

import com.easy.eventbyte.dto.BookingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Connexions SSE ouvertes sur cette instance (GET /bookings/stream), par utilisateur.
 * Chaque utilisateur ne reçoit que ses propres réservations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingStreamService {

    static final String EVENT_NAME = "booking_updated";

    private final Map<String, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();
    private final BookingService bookingService;

    /** Ouvre un flux pour l'utilisateur. Sans timeout : le frontend le garde ouvert toute la session. */
    public SseEmitter open(String userId) {
        SseEmitter emitter = new SseEmitter(0L);
        emittersByUser.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(error -> remove(userId, emitter));
        // Sans premier envoi, la réponse HTTP n'est pas transmise et le client reste en attente de connexion
        send(userId, emitter, SseEmitter.event().comment("connected"));
        return emitter;
    }

    /** Envoie l'état courant de la réservation aux flux ouverts de son propriétaire sur cette instance. */
    public void push(String userId, String bookingId) {
        List<SseEmitter> emitters = emittersByUser.getOrDefault(userId, List.of());
        if (emitters.isEmpty()) {
            return;
        }
        BookingResponse booking = bookingService.getById(userId, bookingId);
        emitters.forEach(emitter -> send(userId, emitter, SseEmitter.event()
                .name(EVENT_NAME)
                .data(booking, MediaType.APPLICATION_JSON)));
    }

    /** Commentaire SSE régulier : garde les connexions ouvertes et détecte les navigateurs partis. */
    @Scheduled(fixedDelayString = "${booking.stream-heartbeat-interval:PT25S}")
    public void heartbeat() {
        emittersByUser.forEach((userId, emitters) ->
                emitters.forEach(emitter -> send(userId, emitter, SseEmitter.event().comment("ping"))));
    }

    /**
     * À l'arrêt de l'application, ferme les flux : sinon l'arrêt gracieux attendrait
     * jusqu'à son délai maximal que ces requêtes sans fin se terminent.
     */
    @EventListener(ContextClosedEvent.class)
    public void closeAll() {
        emittersByUser.values().forEach(emitters -> emitters.forEach(SseEmitter::complete));
        emittersByUser.clear();
    }

    private void send(String userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            // Navigateur parti : la connexion est fermée et oubliée
            remove(userId, emitter);
            emitter.completeWithError(e);
        }
    }

    private void remove(String userId, SseEmitter emitter) {
        emittersByUser.computeIfPresent(userId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
