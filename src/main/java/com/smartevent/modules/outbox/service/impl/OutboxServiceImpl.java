package com.smartevent.modules.outbox.service.impl;

import tools.jackson.databind.ObjectMapper;
import com.smartevent.common.event.DomainEvent;
import com.smartevent.modules.outbox.entity.OutboxEvent;
import com.smartevent.modules.outbox.repository.OutboxEventRepository;
import com.smartevent.modules.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxServiceImpl implements OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY) // Bắt buộc chạy chung Transaction của nghiệp vụ gọi nó
    public void publishEvent(String aggregateType, UUID aggregateId, DomainEvent domainEvent) {
        try {
            String payloadJson = objectMapper.writeValueAsString(domainEvent);
            OutboxEvent outboxEvent = new OutboxEvent(aggregateType, aggregateId, domainEvent.eventType(), payloadJson);
            outboxEventRepository.save(outboxEvent);

            log.info("Outbox Service: Đã lưu sự kiện [{}] cho Aggregate ID: {}",
                    domainEvent.eventType(), aggregateId);
        } catch (Exception ex) {
            log.error("Lỗi khi ghi Outbox Event: {}", ex.getMessage(), ex);
            throw new RuntimeException("Ghi Outbox Event thất bại", ex);
        }
    }
}