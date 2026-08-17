package com.smartevent.ticketing.modules.event.repository;

import com.smartevent.ticketing.modules.event.entity.EventCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventCategoryRepository extends JpaRepository<EventCategory, EventCategory.EventCategoryId> {

    List<EventCategory> findByIdEventId(UUID eventId);

    void deleteByIdEventId(UUID eventId);
}