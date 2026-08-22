package com.smartevent.modules.outbox.support;

import com.smartevent.common.enums.OutboxStatus;
import com.smartevent.config.RabbitMQConfig;
import com.smartevent.infrastructure.messaging.IntegrationEventPublisher;
import com.smartevent.modules.outbox.entity.OutboxEvent;
import com.smartevent.modules.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisherWorker {

    private final OutboxEventRepository outboxEventRepository;
    private final IntegrationEventPublisher integrationEventPublisher;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        if (pendingEvents.isEmpty()) {
            return;
        }

        for (OutboxEvent event : pendingEvents) {
            try {
                String routingKey = resolveRoutingKey(event.getEventType());
                integrationEventPublisher.publish(routingKey, event.getPayloadJson());

                event.markAsPublished();
                outboxEventRepository.save(event);

                log.info("Đã Publish Outbox Event ID {} sang RabbitMQ thành công", event.getId());
            } catch (Exception ex) {
                log.error("Lỗi khi publish Outbox Event ID {}: {}", event.getId(), ex.getMessage());
                event.markAsFailed(ex.getMessage());
                outboxEventRepository.save(event);
            }
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
