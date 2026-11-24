package com.easy.eventbyte.repository;

import com.easy.eventbyte.model.DeletedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeletedEventRepository extends JpaRepository<DeletedEvent, String> {
}
