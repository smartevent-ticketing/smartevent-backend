package com.smartevent.modules.outbox.repository;

import com.smartevent.common.enums.OutboxStatus;
import com.smartevent.modules.outbox.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    List<OutboxEvent> findByStatusOrderByCreatedAtDesc(OutboxStatus status);

    List<OutboxEvent> findByAggregateTypeAndAggregateIdOrderByCreatedAtDesc(String aggregateType, UUID aggregateId);

    long countByStatus(OutboxStatus status);
}