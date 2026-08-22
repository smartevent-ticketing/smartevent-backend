package com.smartevent.modules.outbox.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.common.enums.OutboxStatus;
import com.smartevent.infrastructure.messaging.IntegrationEventPublisher;
import com.smartevent.modules.outbox.entity.OutboxEvent;
import com.smartevent.modules.outbox.repository.OutboxEventRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/outbox")
@RequiredArgsConstructor
@Tag(name = "Outbox & Messaging Monitoring", description = "APIs quản trị viên giám sát hàng đợi Outbox, kiểm tra lỗi và retry sự kiện")
public class OutboxController {

    private final OutboxEventRepository outboxEventRepository;
    private final IntegrationEventPublisher integrationEventPublisher;

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Thống kê số lượng sự kiện Outbox (Pending, Published, Failed)")
    public ApiResponse<Map<String, Long>> getOutboxStats() {
        long pending = outboxEventRepository.countByStatus(OutboxStatus.PENDING);
        long published = outboxEventRepository.countByStatus(OutboxStatus.PUBLISHED);
        long failed = outboxEventRepository.countByStatus(OutboxStatus.FAILED);

        return ApiResponse.success(Map.of(
                "pendingCount", pending,
                "publishedCount", published,
                "failedCount", failed
        ));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lấy danh sách các sự kiện Outbox đang chờ gửi (PENDING)")
    public ApiResponse<List<OutboxEvent>> getPendingEvents() {
        return ApiResponse.success(outboxEventRepository.findByStatusOrderByCreatedAtDesc(OutboxStatus.PENDING));
    }

    @GetMapping("/failed")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lấy danh sách các sự kiện Outbox bị lỗi gửi (FAILED)")
    public ApiResponse<List<OutboxEvent>> getFailedEvents() {
        return ApiResponse.success(outboxEventRepository.findByStatusOrderByCreatedAtDesc(OutboxStatus.FAILED));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Quản trị viên kích hoạt thử lại (Retry) đẩy một sự kiện Outbox sang RabbitMQ")
    public ApiResponse<String> retryEvent(@PathVariable UUID id) {
        OutboxEvent event = outboxEventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Outbox Event"));

        try {
            String routingKey = "event." + event.getEventType().toLowerCase().replace("_", ".");
            integrationEventPublisher.publish(routingKey, event.getPayloadJson());

            event.markAsPublished();
            outboxEventRepository.save(event);
            return ApiResponse.success("Đã retry thành công sự kiện: " + event.getEventType());
        } catch (Exception ex) {
            event.markAsFailed(ex.getMessage());
            outboxEventRepository.save(event);
            throw new RuntimeException("Retry thất bại: " + ex.getMessage(), ex);
        }
    }
}
