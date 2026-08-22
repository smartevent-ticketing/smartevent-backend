package com.smartevent.modules.invoice.repository;

import com.smartevent.modules.invoice.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    // 1. Tìm hóa đơn theo Order ID (Đảm bảo mỗi đơn chỉ có 1 hóa đơn)
    Optional<Invoice> findByOrderId(UUID orderId);

    // 2. Tìm hóa đơn theo mã tra cứu hiển thị (VD: INV-20260822-XXXX)
    Optional<Invoice> findByInvoiceCode(String invoiceCode);

    // 3. Lấy toàn bộ hóa đơn của một người dùng
    List<Invoice> findByUserIdOrderByIssuedAtDesc(UUID userId);

    // 4. Kiểm tra mã hóa đơn đã tồn tại chưa (chống trùng khi sinh mã)
    boolean existsByInvoiceCode(String invoiceCode);

    // 5. Kiểm tra đơn hàng này đã xuất hóa đơn chưa
    boolean existsByOrderId(UUID orderId);
}