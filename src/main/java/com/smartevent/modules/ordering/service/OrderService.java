package com.smartevent.modules.ordering.service;

import com.smartevent.common.api.PageResponse;
import com.smartevent.modules.ordering.dto.request.CreateOrderRequest;
import com.smartevent.modules.ordering.dto.response.OrderResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderService {

    // 1. Tạo đơn hàng từ phiên giữ chỗ 10 phút
    OrderResponse createOrderFromReservation(UUID currentUserId, CreateOrderRequest request);

    // 2. Lấy thông tin đơn hàng theo ID (Chính chủ hoặc ADMIN)
    OrderResponse getOrderById(UUID orderId, UUID currentUserId, boolean isAdmin);

    // 3. Lấy thông tin đơn hàng theo mã đơn orderCode
    OrderResponse getOderByOrderCode(String orderCode, UUID currentUserId, boolean isAdmin);

    // 4. Lấy lịch sử danh sách đơn hàng của người dùng hiện tại (Phân trang)
    PageResponse<OrderResponse> getMyOrders(UUID currentUserId, Pageable pageable);

    // 5. Khách hàng chủ động hủy đơn hàng khi chưa thanh toán (Nhả vé & ghế)
    void cancelOrder(UUID orderId, UUID currentUserId, boolean isAdmin);

    // 6. Quét hết hạn đơn hàng (Đổi sang EXPIRED khi quá 10 phút)
    void expireOrder(UUID orderId);
}
