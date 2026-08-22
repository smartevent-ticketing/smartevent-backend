package com.smartevent.modules.invoice.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.common.security.CurrentUser;
import com.smartevent.infrastructure.security.UserPrincipal;
import com.smartevent.modules.invoice.dto.request.SendInvoiceEmailRequest;
import com.smartevent.modules.invoice.dto.response.InvoiceDeliveryResponse;
import com.smartevent.modules.invoice.dto.response.InvoiceResponse;
import com.smartevent.modules.invoice.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoice & Billing Management", description = "APIs quản lý hóa đơn điện tử, tra cứu chứng từ tài chính và gửi email hóa đơn")
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping("/my-invoices")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy danh sách tất cả các hóa đơn điện tử của người dùng hiện tại")
    public ApiResponse<List<InvoiceResponse>> getMyInvoices(@CurrentUser UserPrincipal currentUser) {
        return ApiResponse.success(invoiceService.getMyInvoices(currentUser.getId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy thông tin chi tiết hóa đơn điện tử theo ID")
    public ApiResponse<InvoiceResponse> getInvoiceById(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        return ApiResponse.success(invoiceService.getInvoiceById(id, currentUser.getId(), isAdmin));
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy hóa đơn điện tử tương ứng của đơn hàng theo Order ID")
    public ApiResponse<InvoiceResponse> getInvoiceByOrderId(
            @PathVariable UUID orderId,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        return ApiResponse.success(invoiceService.getInvoiceByOrderId(orderId, currentUser.getId(), isAdmin));
    }

    @GetMapping("/code/{invoiceCode}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tra cứu hóa đơn điện tử theo mã hiển thị công khai (VD: INV-20260822-ABC12345)")
    public ApiResponse<InvoiceResponse> getInvoiceByCode(
            @PathVariable String invoiceCode,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        return ApiResponse.success(invoiceService.getInvoiceByCode(invoiceCode, currentUser.getId(), isAdmin));
    }

    @PostMapping("/{id}/send-email")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Gửi hoặc gửi lại hóa đơn điện tử đính kèm chi tiết qua email của khách hàng")
    public ApiResponse<InvoiceDeliveryResponse> sendInvoiceEmail(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) SendInvoiceEmailRequest request,
            @CurrentUser UserPrincipal currentUser
    ) {
        return ApiResponse.success(invoiceService.sendInvoiceEmail(id, currentUser.getId(), request));
    }

    private boolean checkIsAdmin(UserPrincipal currentUser) {
        return currentUser != null && currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}