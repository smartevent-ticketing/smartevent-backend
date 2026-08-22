package com.smartevent.modules.outbox.service;

import tools.jackson.databind.ObjectMapper;
import com.smartevent.common.enums.OutboxStatus;
import com.smartevent.modules.ordering.dto.event.OrderPaidEvent;
import com.smartevent.modules.outbox.entity.OutboxEvent;
import com.smartevent.modules.outbox.repository.OutboxEventRepository;
import com.smartevent.modules.outbox.service.impl.OutboxServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxServiceImpl outboxService;

    private UUID orderId;
    private OrderPaidEvent event;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        event = new OrderPaidEvent(orderId, "ORD-20260822-12345", UUID.randomUUID(), "test@gmail.com", BigDecimal.valueOf(500000));
    }

    @Test
    @DisplayName("Ghi nhận Outbox Event thành công vào Database với Status PENDING")
    void publishEvent_Success() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"orderCode\":\"ORD-20260822-12345\"}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> outboxService.publishEvent("ORDER", orderId, event));

        verify(outboxEventRepository, times(1)).save(argThat(e ->
                "ORDER".equals(e.getAggregateType()) &&
                "ORDER_PAID".equals(e.getEventType()) &&
                OutboxStatus.PENDING == e.getStatus()
        ));
    }
}
