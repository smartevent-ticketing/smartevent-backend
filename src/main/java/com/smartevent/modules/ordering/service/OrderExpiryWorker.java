package com.smartevent.modules.ordering.service;

import com.smartevent.common.enums.OrderStatus;
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.ordering.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpiryWorker {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    // Chạy ngầm mỗi 30 giây một lần để quét các đơn hàng quá hạn 10 phút
    @Scheduled(fixedDelay = 30000)
    public void sweepExpiredOrders() {
        Instant now = Instant.now();
        List<Order> expiredOrders = orderRepository.findByStatusAndPaymentDeadlineBefore(OrderStatus.PENDING_PAYMENT, now);

        if (!expiredOrders.isEmpty()) {
            log.info("Phát hiện {} đơn hàng quá hạn thanh toán, tiến hành dọn dẹp...", expiredOrders.size());
            for (Order order : expiredOrders) {
                try {
                    orderService.expireOrder(order.getId());
                } catch (Exception ex) {
                    log.error("Lỗi khi xử lý hết hạn cho đơn hàng {}: {}", order.getOrderCode(), ex.getMessage());
                }
            }
        }
    }
}