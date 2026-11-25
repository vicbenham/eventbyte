package com.easy.eventbyte.exposition;

import com.easy.eventbyte.service.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Likes de l'utilisateur connecté : son identifiant est le sub du JWT. */
@RestController
@RequestMapping("/events/{eventId}/like")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @PostMapping
    public ResponseEntity<Void> like(@PathVariable String eventId, @AuthenticationPrincipal Jwt jwt) {
        likeService.like(jwt.getSubject(), eventId);
        return ResponseEntity.ok().build();
    }
}
