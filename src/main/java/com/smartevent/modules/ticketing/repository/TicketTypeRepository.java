package com.smartevent.modules.ticketing.repository;

import com.smartevent.modules.ticketing.entity.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

    List<TicketType> findByEventId(UUID eventId);

    List<TicketType> findByEventAreaId(UUID eventAreaId);

    boolean existsByEventIdAndName(UUID eventId, String name);

    boolean existsByEventAreaId(UUID eventAreaId);
}

