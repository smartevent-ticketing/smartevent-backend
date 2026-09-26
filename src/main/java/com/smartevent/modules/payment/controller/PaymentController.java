package com.smartevent.modules.payment.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.common.security.CurrentUser;
import com.smartevent.infrastructure.security.UserPrincipal;
import com.smartevent.modules.payment.dto.request.CreatePaymentRequest;
import com.smartevent.modules.payment.dto.response.PaymentResponse;
import com.smartevent.modules.payment.dto.response.VNPayIpnResponse;
import com.smartevent.modules.payment.dto.response.VNPayReturnResponse;
import com.smartevent.modules.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Management", description = "Thanh toán VNPay và xử lý Return/IPN")
public class PaymentController {

    private final PaymentService paymentService;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @PostMapping("/create-url")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Khởi tạo thanh toán và sinh URL chuyển hướng sang cổng thanh toán")
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @CurrentUser UserPrincipal currentUser,
            @Valid @RequestBody CreatePaymentRequest request,
            HttpServletRequest servletRequest) {
        PaymentResponse response = paymentService.createPayment(currentUser.getId(), request, servletRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/vnpay/ipn")
    @Operation(summary = "Webhook IPN tiếp nhận kết quả thanh toán từ Server VNPay (Nguồn sự thật tài chính)")
    public ResponseEntity<VNPayIpnResponse> vnpayIpn(@RequestParam Map<String, String> params) {
        log.info("Nhận VNPay IPN cho đơn {}", params.get("vnp_TxnRef"));
        VNPayIpnResponse response = paymentService.handleVNPayIpn(params);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vnpay/return")
    @Operation(summary = "Return URL tiếp nhận khách hàng quay lại sau khi thanh toán trên VNPay (Chuyển hướng về Frontend UI)")
    public void vnpayReturn(
            @RequestParam Map<String, String> params,
            HttpServletResponse response) throws IOException {
        log.info("Nhận VNPay Return cho đơn {}", params.get("vnp_TxnRef"));
        paymentService.handleVNPayReturn(params);

        StringJoiner query = new StringJoiner("&");
        for (String key : List.of("vnp_TxnRef", "vnp_ResponseCode", "vnp_Amount",
                "vnp_TransactionNo", "vnp_BankCode", "vnp_PayDate")) {
            String value = params.get(key);
            if (value != null) {
                query.add(key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        }
        String redirectTarget = frontendUrl + "/payment/vnpay-return"
                + (query.length() > 0 ? "?" + query : "");
        response.sendRedirect(redirectTarget);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tra cứu thông tin giao dịch thanh toán theo ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentById(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID id) {
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        PaymentResponse response = paymentService.getPaymentById(id, currentUser.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
