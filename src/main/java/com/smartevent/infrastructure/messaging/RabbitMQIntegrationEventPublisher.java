package com.smartevent.infrastructure.messaging;

import tools.jackson.databind.ObjectMapper;
import com.smartevent.common.event.DomainEvent;
import com.smartevent.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQIntegrationEventPublisher implements IntegrationEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(DomainEvent event) {
        try {
            String routingKey = resolveRoutingKey(event.eventType());
            String payloadJson = objectMapper.writeValueAsString(event);
            publish(routingKey, payloadJson);
        } catch (Exception ex) {
            log.error("Lỗi khi chuyển đổi DomainEvent sang JSON: {}", ex.getMessage(), ex);
            throw new RuntimeException("Không thể publish DomainEvent", ex);
        }
    }

    @Override
    public void publish(String routingKey, String payloadJson) {
        log.info("Publishing tin nhắn sang RabbitMQ: [Exchange: {}, RoutingKey: {}]",
                RabbitMQConfig.TOPIC_EXCHANGE, routingKey);
        CorrelationData correlation = new CorrelationData();
        rabbitTemplate.convertAndSend(RabbitMQConfig.TOPIC_EXCHANGE, routingKey, payloadJson, correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.ack() || correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ did not confirm a routed message: "
                        + (correlation.getReturned() != null ? correlation.getReturned().getReplyText() : confirm.reason()));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for RabbitMQ confirm", ex);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ex) {
            throw new IllegalStateException("RabbitMQ publish was not confirmed", ex);
        }
    }

    private String resolveRoutingKey(String eventType) {
        if ("TICKET_ISSUED".equalsIgnoreCase(eventType)) {
            return RabbitMQConfig.ROUTING_TICKET_ISSUED;
        } else if ("INVOICE_CREATED".equalsIgnoreCase(eventType)) {
            return RabbitMQConfig.ROUTING_INVOICE_CREATED;
        } else if ("ORDER_PAID".equalsIgnoreCase(eventType)) {
            return RabbitMQConfig.ROUTING_ORDER_PAID;
        }
        return "event." + eventType.toLowerCase().replace("_", ".");
    }
}
