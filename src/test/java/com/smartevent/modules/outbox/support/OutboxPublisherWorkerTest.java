package com.smartevent.modules.outbox.support;

import com.smartevent.common.enums.OutboxStatus;
import com.smartevent.config.RabbitMQConfig;
import com.smartevent.infrastructure.messaging.IntegrationEventPublisher;
import com.smartevent.modules.outbox.entity.OutboxEvent;
import com.smartevent.modules.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherWorkerTest {

    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private IntegrationEventPublisher integrationEventPublisher;

    @InjectMocks
    private OutboxPublisherWorker worker;

    @Test
    @DisplayName("Worker quét và đẩy sự kiện PENDING sang RabbitMQ thành công, đổi trạng thái sang PUBLISHED")
    void publishPendingEvents_Success() {
        OutboxEvent event = new OutboxEvent("ORDER", UUID.randomUUID(), "ORDER_PAID", "{\"test\":\"json\"}");
        event.setId(UUID.randomUUID());

        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(event));

        worker.publishPendingEvents();

        verify(integrationEventPublisher, times(1)).publish(eq(RabbitMQConfig.ROUTING_ORDER_PAID), eq("{\"test\":\"json\"}"));
        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        verify(outboxEventRepository, times(1)).save(event);
    }

    @Test
    @DisplayName("Worker xử lý lỗi khi RabbitMQ bị sập -> Ghi nhận lỗi và tăng retryCount")
    void publishPendingEvents_Failure_IncrementsRetry() {
        OutboxEvent event = new OutboxEvent("ORDER", UUID.randomUUID(), "ORDER_PAID", "{\"test\":\"json\"}");
        event.setId(UUID.randomUUID());

        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(event));
        doThrow(new RuntimeException("RabbitMQ connection refused"))
                .when(integrationEventPublisher).publish(anyString(), anyString());

        worker.publishPendingEvents();

        assertEquals(1, event.getRetryCount());
        assertEquals("RabbitMQ connection refused", event.getLastError());
        verify(outboxEventRepository, times(1)).save(event);
    }
}
