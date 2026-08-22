package com.smartevent.infrastructure.messaging;

import com.smartevent.common.event.DomainEvent;

public interface IntegrationEventPublisher {

    void publish(DomainEvent event);

    void publish(String routingKey, String payloadJson);
}
