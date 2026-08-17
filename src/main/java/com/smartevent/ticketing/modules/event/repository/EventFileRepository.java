package com.smartevent.ticketing.modules.event.repository;

import com.smartevent.ticketing.common.enums.EventFileType;
import com.smartevent.ticketing.modules.event.entity.EventFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventFileRepository extends JpaRepository<EventFile, UUID> {

    List<EventFile> findByEventIdOrderBySortOrderAsc(UUID eventId);

    List<EventFile> findByEventIdAndFileType(UUID eventId, EventFileType fileType);

    void deleteByEventId(UUID eventId);
}