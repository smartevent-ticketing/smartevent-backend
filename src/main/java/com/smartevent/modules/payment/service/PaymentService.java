package com.smartevent.modules.payment.service;

import com.smartevent.modules.payment.dto.request.CreatePaymentRequest;
import com.smartevent.modules.payment.dto.response.PaymentResponse;
import com.smartevent.modules.payment.dto.response.VNPayIpnResponse;
import com.smartevent.modules.payment.dto.response.VNPayReturnResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.UUID;

public interface PaymentService {

    // 1. Khởi tạo phiên thanh toán và sinh URL chuyển hướng sang cổng (VNPay/MoMo...)
    PaymentResponse createPayment(UUID currentUserId, CreatePaymentRequest request, HttpServletRequest servletRequest);

    // 2. Tiếp nhận và xử lý Webhook IPN từ VNPay (Chốt tiền & xuất vé)
    VNPayIpnResponse handleVNPayIpn(Map<String, String> params);

    // 3. Tiếp nhận Return URL khi khách hàng quay lại website sau thanh toán (Hiển thị UI)
    VNPayReturnResponse handleVNPayReturn(Map<String, String> params);

    // 4. Tra cứu thông tin thanh toán theo ID
    PaymentResponse getPaymentById(UUID paymentId, UUID currentUserId, boolean isAdmin);
}