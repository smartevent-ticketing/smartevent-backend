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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Management", description = "APIs thanh toán đa cổng VNPay, MoMo, ZaloPay, PayPal và xử lý Webhook IPN")
public class PaymentController {

    private final PaymentService paymentService;

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
        log.info("Nhận Webhook IPN từ Server VNPay: {}", params);
        VNPayIpnResponse response = paymentService.handleVNPayIpn(params);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vnpay/return")
    @Operation(summary = "Return URL tiếp nhận khách hàng quay lại sau khi thanh toán trên VNPay (Hiển thị UI)")
    public ResponseEntity<ApiResponse<VNPayReturnResponse>> vnpayReturn(@RequestParam Map<String, String> params) {
        log.info("Khách hàng quay lại từ cổng VNPay Return: {}", params);
        VNPayReturnResponse response = paymentService.handleVNPayReturn(params);
        return ResponseEntity.ok(ApiResponse.success(response));
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