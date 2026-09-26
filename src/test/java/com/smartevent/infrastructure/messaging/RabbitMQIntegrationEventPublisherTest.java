package com.smartevent.infrastructure.messaging;

import com.smartevent.config.RabbitMQConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RabbitMQIntegrationEventPublisherTest {
    @Test
    void waitsForBrokerAckBeforeReturning() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(call -> {
            CorrelationData data = call.getArgument(3);
            data.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).convertAndSend(eq(RabbitMQConfig.TOPIC_EXCHANGE), eq("event.ticket.issued"),
                eq("{}"), any(CorrelationData.class));

        assertDoesNotThrow(() -> new RabbitMQIntegrationEventPublisher(template, mock(ObjectMapper.class))
                .publish("event.ticket.issued", "{}"));
    }

    @Test
    void brokerNackKeepsOutboxEventEligibleForRetry() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(call -> {
            CorrelationData data = call.getArgument(3);
            data.getFuture().complete(new CorrelationData.Confirm(false, "broker refused"));
            return null;
        }).when(template).convertAndSend(eq(RabbitMQConfig.TOPIC_EXCHANGE), eq("event.ticket.issued"),
                eq("{}"), any(CorrelationData.class));

        assertThrows(IllegalStateException.class,
                () -> new RabbitMQIntegrationEventPublisher(template, mock(ObjectMapper.class))
                        .publish("event.ticket.issued", "{}"));
    }
}
