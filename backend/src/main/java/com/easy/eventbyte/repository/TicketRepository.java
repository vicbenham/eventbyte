package com.easy.eventbyte.repository;

import com.easy.eventbyte.model.Ticket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, String> {

    /**
     * Charge les tickets en les verrouillant (SELECT ... FOR UPDATE) jusqu'à la fin de la transaction :
     * deux réservations des mêmes tickets sont traitées l'une après l'autre.
     * Tri par id pour toujours verrouiller dans le même ordre (pas d'interblocage).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.id IN :ids ORDER BY t.id")
    List<Ticket> findAllByIdForUpdate(@Param("ids") Collection<String> ids);
}
