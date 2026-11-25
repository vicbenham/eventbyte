package com.easy.eventbyte.domain;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Synchronisation des tickets d'un événement avec ceux du dernier message Strapi.
 * Idempotente : appliquer deux fois le même message ne crée ni ne supprime rien la seconde fois.
 *
 * @param removed tickets qui ne sont plus dans le message
 * @param added   tickets nouveaux
 * @param kept    tickets déjà connus, à mettre à jour (nom, stock, prix)
 */
public record TicketSync(Set<String> removed, List<String> added, List<String> kept) {

    public static TicketSync between(Collection<String> existingIds, List<String> incomingIds) {
        Set<String> incoming = Set.copyOf(incomingIds);
        return new TicketSync(
                existingIds.stream().filter(id -> !incoming.contains(id)).collect(Collectors.toSet()),
                incomingIds.stream().filter(id -> !existingIds.contains(id)).distinct().toList(),
                incomingIds.stream().filter(existingIds::contains).distinct().toList());
    }

    /**
     * Un message created/updated pour un événement déjà supprimé est ignoré :
     * rejoué ou arrivé après la suppression, il ne doit pas le recréer.
     */
    public static boolean shouldApply(boolean eventDeleted) {
        return !eventDeleted;
    }
}
