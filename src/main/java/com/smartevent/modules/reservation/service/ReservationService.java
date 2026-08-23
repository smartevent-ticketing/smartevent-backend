package com.smartevent.modules.reservation.service;

import com.smartevent.modules.reservation.dto.request.CreateReservationRequest;
import com.smartevent.modules.reservation.dto.response.ReservationResponse;

import java.util.UUID;

public interface ReservationService {

    // 1. Tạo phiên giữ vé 10 phút (Xử lý cả vé đứng và vé ngồi)
    ReservationResponse createReservation(UUID userId, CreateReservationRequest request);

    // 2. Lấy thông tin phiên giữ chỗ theo ID
    ReservationResponse getReservationById(UUID reservationId, UUID currentUserId, boolean isAdmin);

    // 3. Lấy phiên PENDING đang hoạt động của người dùng trên sự kiện (nếu có)
    ReservationResponse getMyActiveReservation(UUID userId, UUID eventId);

    // 4. Khách hàng chủ động hủy phiên giữ chỗ
    void cancelReservation(UUID reservationId, UUID currentUserId, boolean isAdmin);

    // 5. Xác nhận giữ chỗ thành công khi thanh toán xong (Chuyển HELD -> SOLD, trả về true nếu thành công, false nếu đã hết hạn)
    boolean confirmReservation(UUID reservationId);

    // 6. Quét hết hạn 10 phút (Nhả vé và mở khóa ghế)
    void expireReservation(UUID reservationId);
}