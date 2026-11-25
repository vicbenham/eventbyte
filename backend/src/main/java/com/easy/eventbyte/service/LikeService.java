package com.easy.eventbyte.service;

import com.easy.eventbyte.repository.EventLikeRepository;
import com.easy.eventbyte.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class LikeService {

    private final EventRepository eventRepository;
    private final EventLikeRepository eventLikeRepository;

    /** Idempotent : liker deux fois le même événement ne compte qu'un like. */
    public void like(String userId, String eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException(eventId);
        }
        eventLikeRepository.like(userId, eventId);
    }

    /**
     * Idempotent : retirer un like absent, ou celui d'un événement supprimé entre-temps, réussit aussi
     * (le frontend réafficherait le like en cas d'erreur).
     */
    public void unlike(String userId, String eventId) {
        eventLikeRepository.unlike(userId, eventId);
    }
}
