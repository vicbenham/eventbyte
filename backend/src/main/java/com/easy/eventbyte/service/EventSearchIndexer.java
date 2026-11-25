package com.easy.eventbyte.service;

import com.easy.eventbyte.client.MeilisearchClient;
import com.easy.eventbyte.dto.EventDocument;
import com.easy.eventbyte.model.Event;
import com.easy.eventbyte.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * Au démarrage : configure l'index Meilisearch et, s'il est vide, y indexe les événements déjà en base
 * (ingérés avant la recherche, ou après une remise à zéro de Meilisearch).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventSearchIndexer {

    private static final int BATCH_SIZE = 1000;

    private final MeilisearchClient meilisearchClient;
    private final EventRepository eventRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void prepareIndex() {
        try {
            meilisearchClient.configureIndex();
            if (meilisearchClient.countDocuments() == 0) {
                reindexAll();
            }
        } catch (RestClientException e) {
            log.warn("Meilisearch indisponible au démarrage, la recherche ne fonctionnera pas : {}", e.getMessage());
        }
    }

    private void reindexAll() {
        long indexed = 0;
        Page<Event> page = eventRepository.findAll(PageRequest.of(0, BATCH_SIZE, Sort.by("id")));
        while (page.hasContent()) {
            meilisearchClient.index(page.map(EventDocument::from).getContent());
            indexed += page.getNumberOfElements();
            if (!page.hasNext()) {
                break;
            }
            page = eventRepository.findAll(page.nextPageable());
        }
        if (indexed > 0) {
            log.info("{} événement(s) indexé(s) dans Meilisearch", indexed);
        }
    }
}
