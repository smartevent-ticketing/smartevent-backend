package com.smartevent.modules.payment.service.impl;

import com.smartevent.common.enums.OrderStatus;
import com.smartevent.common.enums.PaymentMethod;
import com.smartevent.common.enums.PaymentStatus;
import com.smartevent.common.util.VNPayUtils;
import com.smartevent.config.VNPayProperties;
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.ordering.service.OrderLifecycleService;
import com.smartevent.modules.payment.dto.response.VNPayIpnResponse;
import com.smartevent.modules.payment.dto.response.VNPayReturnResponse;
import com.smartevent.modules.payment.entity.Payment;
import com.smartevent.modules.payment.entity.PaymentWebhookEvent;
import com.smartevent.modules.payment.repository.PaymentRepository;
import com.smartevent.modules.payment.repository.PaymentWebhookEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class VNPayCallbackHandler {

    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final OrderLifecycleService orderLifecycleService;
    private final VNPayProperties vnPayProperties;
    private final ObjectMapper objectMapper;
    private final PaymentCompletionService paymentCompletionService;
    private final com.smartevent.modules.payment.service.PaymentReconciliationService reconciliationService;

    @Transactional
    public VNPayIpnResponse handleVNPayIpn(Map<String, String> params) {
        log.info("Nhận thông báo VNPay cho đơn {}", params.get("vnp_TxnRef"));

        String secureHash = params.get("vnp_SecureHash");
        String orderCode = params.get("vnp_TxnRef");
        String transactionNo = params.get("vnp_TransactionNo");
        String amountStr = params.get("vnp_Amount");
        String responseCode = params.get("vnp_ResponseCode");

        // BƯỚC 1: Xác thực chữ ký số HMAC-SHA512
        boolean isValidSignature = VNPayUtils.verifySignature(params, secureHash, vnPayProperties.getHashSecret());
        if (!isValidSignature) {
            log.error("Chữ ký Webhook IPN VNPay không hợp lệ!");
            return VNPayIpnResponse.invalidChecksum();
        }
        if (orderCode == null || orderCode.isBlank() || transactionNo == null || transactionNo.isBlank()
                || !java.util.Objects.equals(vnPayProperties.getTmnCode(), params.get("vnp_TmnCode"))
                || responseCode == null || !responseCode.matches("[0-9]{2}")
                || params.get("vnp_TransactionStatus") == null || !params.get("vnp_TransactionStatus").matches("[0-9]{2}")) {
            return VNPayIpnResponse.unknownError();
        }

        // BƯỚC 2: Khóa đơn hàng cho toàn bộ transaction xử lý thanh toán
        var orderOpt = orderLifecycleService.lockForPayment(orderCode);
        if (orderOpt.isEmpty()) {
            log.error("Không tìm thấy đơn hàng có mã {} từ IPN VNPay", orderCode);
            return VNPayIpnResponse.orderNotFound();
        }
        Order order = orderOpt.get();

        // BƯỚC 3: Kiểm tra Idempotency sau khi đã giữ khóa đơn hàng
        String providerEventId = orderCode + "_" + transactionNo;
        if (webhookEventRepository.existsByProviderAndProviderEventId("VNPAY", providerEventId)) {
            log.warn("Sự kiện Webhook VNPay {} đã được xử lý trước đó (Idempotency Guard)", providerEventId);
            return VNPayIpnResponse.orderAlreadyConfirmed();
        }

        // BƯỚC 4: Kiểm tra toàn vẹn số tiền (Amount Integrity Check)
        BigDecimal vnpAmount;
        try {
            if (amountStr == null || !amountStr.matches("[0-9]+")) return VNPayIpnResponse.invalidAmount();
            vnpAmount = new BigDecimal(amountStr).divide(BigDecimal.valueOf(100));
        } catch (NumberFormatException | NullPointerException ex) {
            return VNPayIpnResponse.invalidAmount();
        }
        if (order.getTotalAmount().compareTo(vnpAmount) != 0) {
            log.error("Sai lệch số tiền! Đơn hàng: {}, VNPay gửi: {}", order.getTotalAmount(), vnpAmount);
            return VNPayIpnResponse.invalidAmount();
        }

        // A repeated transaction is idempotent; a distinct extra charge must be recorded for reconciliation.
        var existingPayment = paymentRepository.findByProviderAndTransactionId("VNPAY", transactionNo);
        if (existingPayment.isPresent() && !existingPayment.get().getOrderId().equals(order.getId())) {
            return VNPayIpnResponse.unknownError();
        }
        if (existingPayment.filter(Payment::isSuccess).isPresent()) {
            return VNPayIpnResponse.orderAlreadyConfirmed();
        }

        // BƯỚC 6: Xử lý thành công vs thất bại
        Payment payment = existingPayment.orElseGet(() ->
                paymentRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(order.getId(), PaymentStatus.INITIATED)
                        .orElseGet(() -> new Payment(order.getId(), PaymentMethod.VNPAY, "VNPAY", vnpAmount)));

        payment.setTransactionId(transactionNo);

        boolean alreadyPaid = order.isPaid();
        if ("00".equals(responseCode) && "00".equals(params.get("vnp_TransactionStatus"))) {
            paymentCompletionService.complete(order, payment, transactionNo);
        } else {
            // Thanh toán thất bại hoặc khách bấm hủy
            payment.setStatus(PaymentStatus.FAILED);
            log.warn("Giao dịch VNPay thất bại cho đơn hàng {} với mã lỗi {}", orderCode, responseCode);
        }

        paymentRepository.save(payment);
        if (payment.isSuccess() && (alreadyPaid || !order.isPaid())) {
            reconciliationService.requireReview(payment, alreadyPaid ? "DUPLICATE_PAYMENT" : "LATE_PAYMENT_EXPIRED");
        }

        // BƯỚC 7: Lưu bản ghi Webhook Event trong cùng transaction
        try {
            String payloadJson = objectMapper.writeValueAsString(params);
            PaymentWebhookEvent webhookEvent = new PaymentWebhookEvent("VNPAY", providerEventId, transactionNo, secureHash, payloadJson);
            webhookEvent.setStatus("PROCESSED");
            webhookEvent.setProcessedAt(Instant.now());
            webhookEventRepository.save(webhookEvent);
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể lưu kết quả xử lý webhook", ex);
        }

        log.info("Xử lý IPN VNPay thành công cho đơn hàng {}", orderCode);
        return VNPayIpnResponse.success();
    }

    @Transactional
    public VNPayReturnResponse handleVNPayReturn(Map<String, String> params) {
        // Both entry points execute the same validation, idempotency and financial writes.
        VNPayIpnResponse outcome = handleVNPayIpn(params);
        String orderCode = params.get("vnp_TxnRef");
        String transactionNo = params.get("vnp_TransactionNo");
        String bankCode = params.get("vnp_BankCode");
        String amountStr = params.get("vnp_Amount");
        String payDate = params.get("vnp_PayDate");
        BigDecimal amount = amountStr != null && amountStr.matches("[0-9]+")
                ? new BigDecimal(amountStr).movePointLeft(2) : BigDecimal.ZERO;
        if (!"00".equals(outcome.rspCode()) && !"02".equals(outcome.rspCode())) {
            return new VNPayReturnResponse(orderCode, transactionNo, bankCode, amount, payDate, "FAILED", outcome.message());
        }
        var order = orderLifecycleService.lockForPayment(orderCode);
        String status = order.filter(Order::isPaid).isPresent() ? "SUCCESS" : "PENDING";
        if (order.filter(o -> o.getStatus() == OrderStatus.CANCELLED).isPresent()) status = "REFUND_REQUIRED";
        return new VNPayReturnResponse(orderCode, transactionNo, bankCode, amount, payDate, status,
                "SUCCESS".equals(status) ? "Đơn hàng đã được xác nhận thanh toán" : "Vui lòng kiểm tra trạng thái đơn hàng");
    }
}
