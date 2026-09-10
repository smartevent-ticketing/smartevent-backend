package com.smartevent.modules.event.repository;

import com.smartevent.modules.event.entity.EventCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventCategoryRepository extends JpaRepository<EventCategory, EventCategory.EventCategoryId> {

    List<EventCategory> findByIdEventId(UUID eventId);

    List<EventCategory> findByIdEventIdIn(List<UUID> eventIds);

    void deleteByIdEventId(UUID eventId);
}
