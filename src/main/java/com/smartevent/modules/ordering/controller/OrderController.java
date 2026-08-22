package com.smartevent.modules.ordering.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.common.api.PageResponse;
import com.smartevent.common.pagination.PageRequestUtils;
import com.smartevent.common.security.CurrentUser;
import com.smartevent.infrastructure.security.UserPrincipal;
import com.smartevent.modules.ordering.dto.request.CreateOrderRequest;
import com.smartevent.modules.ordering.dto.response.OrderResponse;
import com.smartevent.modules.ordering.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Ordering Management", description = "APIs quản lý vòng đời đơn hàng và đóng băng giá")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tạo đơn hàng từ phiên giữ chỗ 10 phút")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @CurrentUser UserPrincipal currentUser,
            @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrderFromReservation(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy thông tin chi tiết đơn hàng theo ID")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID id) {
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        OrderResponse response = orderService.getOrderById(id, currentUser.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/code/{orderCode}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy thông tin chi tiết đơn hàng theo mã đơn orderCode")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderByCode(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable String orderCode) {
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        OrderResponse response = orderService.getOderByOrderCode(orderCode, currentUser.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/my-orders")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy danh sách lịch sử đơn hàng của người dùng hiện tại")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getMyOrders(
            @CurrentUser UserPrincipal currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {
        Pageable pageable = PageRequestUtils.of(page, size, sortBy, sortDirection);
        PageResponse<OrderResponse> response = orderService.getMyOrders(currentUser.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Khách hàng chủ động hủy đơn hàng (nhả vé và ghế)")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(
            @CurrentUser UserPrincipal currentUser,
            @PathVariable UUID id) {
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        orderService.cancelOrder(id, currentUser.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.ok("Đã hủy đơn hàng thành công"));
    }
}