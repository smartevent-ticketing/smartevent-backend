# 💳 MODULE 6: PAYMENT GATEWAYS & SECURITY ARCHITECTURE
## (TÍCH HỢP CỔNG THANH TOÁN VNPAY / MOMO / ZALOPAY / PAYPAL, CHỮ KÝ SỐ & IDEMPOTENCY IPN)

**Trạng thái:** Đang triển khai · Sẵn sàng tích hợp Code  
**Tác giả / Hệ thống:** Backend Engineering Team · Smart Event Ticketing Platform  

---

## 🧭 1. Tổng Quan Kiến Trúc Đa Cổng Thanh Toán

Hệ thống Smart Event Ticketing Platform được thiết kế linh hoạt hỗ trợ 4 cổng thanh toán lớn theo mô hình **Strategy Pattern**:

| Cổng Thanh Toán | Loại Hình | Thuật Toán Chữ Ký | Điểm Nổi Bật |
|---|---|:---:|---|
| **VNPay** | Cổng thanh toán quốc dân | `HMAC-SHA512` | Hỗ trợ VNPAY-QR, Thẻ ATM 40+ ngân hàng Việt Nam, Visa/Master |
| **MoMo** | Ví điện tử nội địa | `HMAC-SHA256` | Quét mã QR Ví MoMo nhanh chóng trên di động |
| **ZaloPay** | Ví điện tử hệ sinh thái Zalo | `HMAC-SHA256` | Cơ chế bảo mật 2 chiều với 2 khóa (`Key1` tạo đơn, `Key2` verify callback) |
| **PayPal** | Cổng thanh toán quốc tế | `OAuth2 / REST API` | Chuẩn thanh toán thẻ quốc tế cho khách hàng toàn cầu |

---

## 🔄 2. Sơ Đồ 2 Kênh Giao Tiếp: Return URL vs IPN Webhook

Trong mọi giao dịch thanh toán trực tuyến, luôn luôn có **2 luồng dữ liệu độc lập**:

```
                            [ KHÁCH HÀNG BẤM "THANH TOÁN" ]
                                          │
                                          ▼
                       [ KHỞI TẠO PAYMENT TRÊN BACKEND ]
             (Tạo Payment INITIATED + Ký số HMAC-SHA512 + Sinh Pay URL)
                                          │
                                          ▼
                      [ CHUYỂN HƯỚNG SANG CỔNG VNPAY/MOMO ]
                                          │
                  ┌───────────────────────┴───────────────────────┐
                  ▼                                               ▼
     【 KÊNH 1: BROWSER REDIRECT 】                 【 KÊNH 2: SERVER WEBHOOK (IPN) 】
     • Endpoint: `/vnpay/return`                   • Endpoint: `/vnpay/ipn`
     • Phục vụ trải nghiệm người dùng (UI)         • NGUỒN SỰ THẬT TÀI CHÍNH (Source of Truth)
     • Hiển thị màn hình: "Đang xử lý..."          • Server VNPay gọi ngầm sang Server của ta
     • Không dùng để chốt tiền hay xuất vé!        • Chạy ngầm, an toàn, không phụ thuộc trình duyệt
                  │                                               │
                  └───────────────────────┬───────────────────────┘
                                          ▼
                      【 XỬ LÝ IPN HOÀN TẤT TRONG DATABASE 】
                      1. Xác thực chữ ký HMAC-SHA512
                      2. Chống xử lý lặp (Idempotency Guard)
                      3. Kiểm tra đúng số tiền (Amount Check)
                      4. Đổi Payment: INITIATED -> SUCCESS
                      5. Đổi Order: PENDING_PAYMENT -> PAID
                      6. Gọi confirmReservation() -> Ghế HELD -> SOLD
                      7. Trả kết quả về VNPay: {"RspCode":"00"}
```

---

## 🗃️ 3. Lược Đồ Database (`V8__payment_schema.sql`)

```sql
-- 1. Bảng Lịch Sử Thanh Toán
CREATE TABLE payments (
    id             UUID PRIMARY KEY,
    order_id       UUID NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    payment_method VARCHAR(30) NOT NULL,
    provider       VARCHAR(30) NOT NULL DEFAULT 'VNPAY',
    transaction_id VARCHAR(100),
    amount         DECIMAL(15,2) NOT NULL,
    currency       VARCHAR(10) NOT NULL DEFAULT 'VND',
    status         VARCHAR(30) NOT NULL DEFAULT 'INITIATED',
    paid_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payments_amount CHECK (amount > 0)
);

CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_provider_tx ON payments(provider, transaction_id) WHERE transaction_id IS NOT NULL;
CREATE INDEX idx_payments_status ON payments(status);

-- 2. Bảng Ghi Nhận Webhook Event (Kiểm Soát Idempotency)
CREATE TABLE payment_webhook_events (
    id                UUID PRIMARY KEY,
    provider          VARCHAR(30) NOT NULL,
    provider_event_id VARCHAR(100) NOT NULL,
    transaction_id    VARCHAR(100),
    payload_hash      VARCHAR(64),
    payload_json      TEXT,
    received_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at      TIMESTAMPTZ,
    status            VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    CONSTRAINT uk_webhook_provider_event UNIQUE (provider, provider_event_id)
);

CREATE INDEX idx_webhook_events_provider_event ON payment_webhook_events(provider, provider_event_id);
CREATE INDEX idx_webhook_events_status ON payment_webhook_events(status);
```

---

## 🔐 4. Thuật Toán 8 Bước Xử Lý IPN Webhook Chuẩn Ngân Hàng

Khi Server VNPay gửi thông báo IPN tới `/api/v1/payments/vnpay/ipn`, hệ thống thực thi tuần tự **8 bước nghiêm ngặt**:

1. **Bước 1: Verify Digital Signature (Xác Thực Chữ Ký):**
   * Lấy toàn bộ tham số gửi sang (trừ `vnp_SecureHash`), sắp xếp theo thứ tự bảng chữ cái (Alphabetical Order), nối chuỗi và băm `HmacSHA512` với `vnp_HashSecret`.
   * So sánh với chữ ký `vnp_SecureHash` gửi kèm. Nếu không khớp $\rightarrow$ Trả về `{"RspCode":"97", "Message":"Invalid Checksum"}`.
2. **Bước 2: Idempotency Guard (Chống Xử Lý Trùng Lặp):**
   * Kiểm tra trong bảng `payment_webhook_events` theo `provider_event_id = vnp_TransactionNo`.
   * Nếu sự kiện này đã ở trạng thái `PROCESSED` $\rightarrow$ Trả về ngay `{"RspCode":"02", "Message":"Order already confirmed"}`.
3. **Bước 3: Order Lookup (Tìm Đơn Hàng):**
   * Tìm `Order` theo mã `orderCode = vnp_TxnRef`.
   * Nếu không tìm thấy $\rightarrow$ Trả về `{"RspCode":"01", "Message":"Order not found"}`.
4. **Bước 4: Amount Integrity Check (Kiểm Tra Toàn Vẹn Số Tiền):**
   * VNPay quy ước số tiền nhân 100 (`vnp_Amount = 50000000` đại diện cho 500,000 VNĐ).
   * Lấy `vnp_Amount / 100` so sánh với `order.getTotalAmount()`. Nếu sai lệch $\rightarrow$ Báo động gian lận, trả về `{"RspCode":"04", "Message":"Invalid Amount"}`.
5. **Bước 5: Order Status Guard:**
   * Nếu `order.getStatus() == OrderStatus.PAID` $\rightarrow$ Trả về `{"RspCode":"02", "Message":"Order already confirmed"}`.
6. **Bước 6: Xử Lý Phân Nhánh Thành Công vs Thất Bại:**
   * **Nếu Giao Dịch Thành Công (`vnp_ResponseCode == "00"`):**
     * Cập nhật `Payment`: `status = SUCCESS`, `transactionId = vnp_TransactionNo`, `paidAt = NOW()`.
     * Cập nhật `Order`: `status = PAID`.
     * Kích hoạt chuỗi hậu thanh toán: Gọi `ReservationService.confirmReservation(reservationId)` để chuyển ghế `HELD` $\rightarrow$ `SOLD` và chốt hạn mức user.
     * Cập nhật `PaymentWebhookEvent`: `status = "PROCESSED"`, `processedAt = NOW()`.
     * Trả về VNPay: `{"RspCode":"00", "Message":"Confirm Success"}`.
   * **Nếu Giao Dịch Thất Bại (`vnp_ResponseCode != "00"`):**
     * Cập nhật `Payment`: `status = FAILED`.
     * Trả về VNPay: `{"RspCode":"00", "Message":"Confirm Success"}` (để VNPay biết hệ thống đã nhận tin và không retry nữa).

---

## 🛡️ 5. Các Cảnh Báo An Ninh Tài Chính Cần Lưu Ý

> [!CAUTION]
> **1. Tuyệt Đối Không Tin Tưởng Kênh Return URL Để Xuất Vé:**  
> Kênh Return URL chạy qua trình duyệt của người dùng, hacker có thể can thiệp giả lập tham số URL để lừa hệ thống. **Chỉ duy nhất kênh Webhook IPN mới được phép cập nhật đơn hàng thành `PAID` và phát hành vé**.

> [!IMPORTANT]
> **2. Kiểm Tra Tính Toàn Vẹn Của Số Tiền (Amount Integrity):**  
> Luôn so sánh `vnp_Amount / 100` với `order.total_amount` trong Database. Nếu hacker chỉnh sửa số tiền từ 1,000,000 VNĐ xuống 10,000 VNĐ trên cổng thanh toán, hệ thống sẽ phát hiện lệch số tiền và từ chối giao dịch ngay lập tức.
