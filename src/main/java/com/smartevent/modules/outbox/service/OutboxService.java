package com.smartevent.modules.outbox.service;

import com.smartevent.common.event.DomainEvent;

import java.util.UUID;

public interface OutboxService {

    void publishEvent(String aggregateType, UUID aggregateId, DomainEvent domainEvent);
}