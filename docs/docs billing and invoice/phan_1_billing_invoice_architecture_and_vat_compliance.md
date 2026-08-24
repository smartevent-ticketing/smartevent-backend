# 🧾 MODULE 8 - PHẦN 1: TỔNG QUAN KIẾN TRÚC HÓA ĐƠN ĐIỆN TỬ & NGUYÊN TẮC TÀI CHÍNH
## (E-COMMERCE BILLING PIPELINE, EXACTLY-ONCE SEMANTICS & FINANCIAL COMPLIANCE)

**Trạng thái:** Hoàn thành 100% · ACID & Idempotency Guaranteed  
**Tác giả / Hệ thống:** Backend Engineering Team · Smart Event Ticketing Platform  

---

## 📖 1. Từ Điển Thuật Ngữ Nghiệp Vụ & Kỹ Thuật (Domain Terminology)

Để hiểu sâu sắc về phân hệ Hóa đơn & Báo cáo tài chính, chúng ta cần nắm vững các khái niệm chuẩn quốc tế sau:

| Thuật Ngữ (Term) | Tên Tiếng Việt | Định Nghĩa & Ý Nghĩa Nghiệp Vụ Trong Hệ Thống |
|---|---|---|
| **Billing vs Invoicing** | Lập Hóa Đơn & Thanh Toán | **Billing** là toàn bộ quy trình tính toán tiền (vé, phí, giảm giá); **Invoicing** là việc phát hành chứng từ hóa đơn pháp lý chính thức sau khi thu được tiền. |
| **Exactly-Once Issuance** | Phát Hành Đúng Một Lần | Nguyên tắc bất di bất dịch: **Mỗi đơn hàng `PAID` chỉ được phép sinh ra duy nhất 1 hóa đơn**. Không bao giờ có chuyện 1 đơn hàng bị xuất 2 hóa đơn (tránh trùng thuế và sai lệch doanh thu). |
| **Idempotency** | Tính Bất Khả Biến (Lũy Đẳng) | Dù hệ thống có bị gọi lại API xuất hóa đơn 10 lần (do mạng lag, retry), hệ thống vẫn chỉ trả về đúng hóa đơn ban đầu mà không tạo thêm dữ liệu rác. |
| **Financial Immutability** | Đóng Băng Dữ Liệu Tài Chính | Khi hóa đơn đã được phát hành (`ISSUED`), **toàn bộ số tiền và mô tả dòng vé không bao giờ được phép sửa đổi**. Nếu cần sửa, phải hủy (`VOID`) và xuất hóa đơn điều chỉnh. |
| **Subtotal** | Tiền Vé Gốc | Tổng giá trị danh nghĩa của các vé trước khi áp dụng mã giảm giá và phí tiện ích. |
| **Discount Amount** | Tiền Giảm Giá | Số tiền được khấu trừ từ Voucher, Coupon khuyến mãi hoặc chính sách chiết khấu của Ban tổ chức. |
| **Fee Amount** | Phí Dịch Vụ / Tiện Ích | Phí xử lý cổng thanh toán hoặc phí nền tảng (Convenience Fee / Platform Fee). |
| **Total Amount (Grand Total)** | Tổng Tiền Thanh Toán | Số tiền thực thu cuối cùng = $\text{Subtotal} - \text{Discount Amount} + \text{Fee Amount}$. |

---

## 🧭 2. Quy Trình Vận Hành Xuất Hóa Đơn Tự Động (Billing Pipeline)

Hóa đơn điện tử **CHỈ ĐƯỢC PHÉP SINH RA SAU KHI TIỀN ĐÃ VÀO TÀI KHOẢN** (khi Order chuyển sang `PAID`):

```
                   [ KHÁCH HÀNG QUẸT THẺ THÀNH CÔNG TRÊN CỔNG THANH TOÁN ]
                                             │
                                             ▼
             ┌───────────────────────────────────────────────────────────────┐
             │  1. `PaymentServiceImpl` nhận IPN Webhook mã phản hồi "00"    │
             │  2. `ReservationService.confirmReservation(reservationId)`    │
             │  3. `TicketService.issueTicketsForOrder(orderId)`             │
             │  4. `InvoiceService.issueInvoiceForOrder(orderId)`            │
             └───────────────────────────────────────────────────────────────┘
                                             │
                                             ▼
 ┌─────────────────────────────────────────────────────────────────────────────────┐
 │  TIẾN TRÌNH XUẤT HÓA ĐƠN ĐỘC NHẤT (`InvoiceServiceImpl.issueInvoiceForOrder`):  │
 │                                                                                 │
 │  • BƯỚC 1 (Kiểm soát Idempotency):                                              │
 │    - Kiểm tra `invoiceRepository.findByOrderId(orderId)`.                       │
 │    - Nếu đã tồn tại ──► Trả về ngay hóa đơn cũ (Không tạo mới).                 │
 │                                                                                 │
 │  • BƯỚC 2 (Xác thực trạng thái đơn):                                            │
 │    - Kiểm tra `order.getStatus() == OrderStatus.PAID`.                          │
 │    - Nếu đơn chưa trả tiền (`PENDING_PAYMENT`) ──► Ném lỗi `ORDER_INVALID_STATUS`│
 │                                                                                 │
 │  • BƯỚC 3 (Đóng băng thông tin đầu hóa đơn `invoices`):                         │
 │    - Sinh mã hóa đơn hiển thị duy nhất: `INV-yyyyMMdd-XXXXXXXX`                 │
 │    - Lưu `subtotal`, `discount_amount`, `fee_amount`, `total_amount`.           │
 │    - Gán `billing_email` của người mua vé.                                      │
 │                                                                                 │
 │  • BƯỚC 4 (Bóc tách dòng hóa đơn `invoice_items`):                              │
 │    - Duyệt qua từng mục vé `OrderItem` để đóng băng mô tả và đơn giá.           │
 │                                                                                 │
 │  • BƯỚC 5 (Kích hoạt theo dõi gửi email `invoice_deliveries`):                  │
 │    - Tạo bản ghi theo dõi gửi email hóa đơn PDF (Status: `SENT`).               │
 └─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🗃️ 3. Lược Đồ Bảng Database Flyway V10 (`invoices`)

```sql
CREATE TABLE invoices (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    invoice_code VARCHAR(100) NOT NULL UNIQUE,
    billing_email VARCHAR(255) NOT NULL,
    subtotal DECIMAL(15,2) NOT NULL,
    discount_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    fee_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(15,2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ISSUED',
    issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    file_id UUID REFERENCES files(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- Ràng buộc toàn vẹn số tiền không âm
    CONSTRAINT chk_invoices_amounts CHECK (
        subtotal >= 0 AND 
        total_amount >= 0 AND 
        discount_amount >= 0 AND 
        fee_amount >= 0
    )
);

-- Chỉ mục tối ưu hóa truy vấn
CREATE INDEX idx_invoices_order_id ON invoices(order_id);
CREATE INDEX idx_invoices_user_id ON invoices(user_id);
CREATE INDEX idx_invoices_invoice_code ON invoices(invoice_code);
CREATE INDEX idx_invoices_status ON invoices(status);
```

### 🔒 Giải Thích Các Ràng Buộc Kỹ Thuật:
1. **`order_id UUID NOT NULL UNIQUE`:** Đảm bảo quan hệ $1:1$ tuyệt đối giữa Đơn hàng và Hóa đơn. Không một đơn hàng nào có thể có 2 hóa đơn.
2. **`ON DELETE RESTRICT`:** Ngăn chặn tuyệt đối việc xóa Đơn hàng hoặc User khi đã phát sinh hóa đơn tài chính (bảo vệ lịch sử kiểm toán thuế).
3. **`chk_invoices_amounts`:** Bảo vệ mức Database chống lại các lỗi logic làm âm số tiền.
