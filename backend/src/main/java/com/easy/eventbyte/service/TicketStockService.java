package com.easy.eventbyte.service;

import com.easy.eventbyte.model.Ticket;
import com.easy.eventbyte.repository.BookingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Stock des tickets dans Redis : un compteur de places disponibles par ticket, décrémenté atomiquement.
 * C'est lui qui garantit qu'un ticket n'est jamais survendu, même avec des réservations simultanées.
 * La base reste la source de vérité : un compteur absent est recalculé depuis les réservations enregistrées.
 */
@Slf4j
@Service
public class TicketStockService {

    private static final RedisScript<Long> RESERVE_SCRIPT =
            RedisScript.of(new ClassPathResource("redis/reserve-stock.lua"), Long.class);

    private final StringRedisTemplate redis;
    private final BookingRepository bookingRepository;

    public TicketStockService(StringRedisTemplate redis, BookingRepository bookingRepository) {
        this.redis = redis;
        this.bookingRepository = bookingRepository;
    }

    static String key(String ticketId) {
        return "stock:ticket:" + ticketId;
    }

    /**
     * Réserve les quantités demandées sur tous les tickets, ou rien si un seul en manque.
     * Si la transaction en cours échoue ensuite, les places sont automatiquement rendues.
     */
    public boolean tryReserve(List<Ticket> tickets, Map<String, Integer> quantities) {
        initializeMissingCounters(tickets);

        List<String> keys = tickets.stream().map(t -> key(t.getId())).toList();
        Object[] amounts = tickets.stream().map(t -> String.valueOf(quantities.get(t.getId()))).toArray();
        boolean reserved = Long.valueOf(1).equals(redis.execute(RESERVE_SCRIPT, keys, amounts));

        if (reserved) {
            releaseIfTransactionRollsBack(quantities);
        }
        return reserved;
    }

    /** Rend des places au stock (réservation expirée ou annulée). */
    public void release(Map<String, Integer> quantities) {
        quantities.forEach((ticketId, quantity) -> {
            // Inutile de rendre à un compteur absent : il sera recalculé depuis la base
            if (Boolean.TRUE.equals(redis.hasKey(key(ticketId)))) {
                redis.opsForValue().increment(key(ticketId), quantity);
            }
        });
    }

    /** Rend des places au stock une fois la transaction validée (rien si elle est annulée). */
    public void releaseAfterCommit(Map<String, Integer> quantities) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            release(quantities);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                release(quantities);
            }
        });
    }

    /** Oublie les compteurs (stock modifié dans Strapi) une fois la transaction validée : ils seront recalculés. */
    public void invalidateAfterCommit(Collection<String> ticketIds) {
        Runnable invalidate = () -> redis.delete(ticketIds.stream().map(TicketStockService::key).toList());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidate.run();
                }
            });
        } else {
            invalidate.run();
        }
    }

    /** Compteur absent : stock Strapi moins les places des réservations déjà enregistrées. */
    private void initializeMissingCounters(List<Ticket> tickets) {
        List<Ticket> missing = tickets.stream()
                .filter(t -> !Boolean.TRUE.equals(redis.hasKey(key(t.getId()))))
                .toList();
        if (missing.isEmpty()) {
            return;
        }
        Map<String, Long> held = bookingRepository.heldByTicketId(missing.stream().map(Ticket::getId).toList());
        for (Ticket ticket : missing) {
            int available = ticket.availableQuantity(held.getOrDefault(ticket.getId(), 0L));
            // SET NX : si une autre requête l'a initialisé entre-temps, sa valeur est conservée
            redis.opsForValue().setIfAbsent(key(ticket.getId()), String.valueOf(available));
        }
    }

    private void releaseIfTransactionRollsBack(Map<String, Integer> quantities) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    log.warn("Transaction annulée : places rendues au stock {}", quantities);
                    release(quantities);
                }
            }
        });
    }
}
