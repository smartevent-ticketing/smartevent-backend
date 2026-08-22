package com.smartevent.infrastructure.messaging;

import tools.jackson.databind.ObjectMapper;
import com.smartevent.common.event.DomainEvent;
import com.smartevent.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

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
        rabbitTemplate.convertAndSend(RabbitMQConfig.TOPIC_EXCHANGE, routingKey, payloadJson);
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
