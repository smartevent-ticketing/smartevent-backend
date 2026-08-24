# 📋 MODULE 6: REST APIS, OPENAPI SWAGGER & MOCKITO UNIT TEST SUITE
## (HỆ THỐNG APIS ĐƠN HÀNG, THANH TOÁN, TÀI LIỆU SWAGGER & BỘ TEST TỰ ĐỘNG)

**Trạng thái:** Hoàn thành 100% · 23/23 Tests Pass Xanh  
**Tác giả / Hệ thống:** Backend Engineering Team · Smart Event Ticketing Platform  

---

## 🧭 1. Tổng Quan Kiến Trúc & Cấu Trúc File Thực Tế

Toàn bộ mã nguồn Module 6 tuân thủ nghiêm ngặt mô hình **Modular Monolith** và **Strategy Pattern**:

```
src/
  ├── main/java/com/smartevent/
  │     ├── config/
  │     │     ├── VNPayProperties.java & VNPayConfig.java       ← Cấu hình cổng VNPay
  │     │     ├── MoMoProperties.java & MoMoConfig.java         ← Cấu hình ví MoMo
  │     │     ├── ZaloPayProperties.java & ZaloPayConfig.java   ← Cấu hình ví ZaloPay
  │     │     └── PayPalProperties.java & PayPalConfig.java     ← Cấu hình cổng PayPal
  │     ├── common/util/
  │     │     ├── VNPayUtils.java                               ← Thuật toán HMAC-SHA512 & Sort Alphabet
  │     │     ├── MoMoUtils.java                                ← Thuật toán MoMo HMAC-SHA256
  │     │     └── ZaloPayUtils.java                             ← Thuật toán ZaloPay HMAC-SHA256 (2 Khóa)
  │     ├── modules/ordering/
  │     │     ├── entity/ (Order.java, OrderItem.java)
  │     │     ├── repository/ (OrderRepository.java, OrderItemRepository.java)
  │     │     ├── dto/ (CreateOrderRequest.java, OrderResponse.java, OrderItemResponse.java)
  │     │     ├── service/ (OrderService.java, OrderServiceImpl.java, OrderExpiryWorker.java)
  │     │     └── controller/ (OrderController.java)
  │     └── modules/payment/
  │           ├── entity/ (Payment.java, PaymentWebhookEvent.java)
  │           ├── repository/ (PaymentRepository.java, PaymentWebhookEventRepository.java)
  │           ├── dto/ (CreatePaymentRequest.java, PaymentResponse.java, VNPayIpnResponse.java, VNPayReturnResponse.java)
  │           ├── service/ (PaymentGatewayProvider.java, VNPayGatewayProvider.java, PaymentService.java, PaymentServiceImpl.java)
  │           └── controller/ (PaymentController.java)
  └── test/java/com/smartevent/
        ├── modules/ordering/service/OrderServiceTest.java       ← 12 Test Cases (100% PASS)
        └── modules/payment/service/PaymentServiceTest.java      ← 11 Test Cases (100% PASS)
```

---

## 🌐 2. Chi Tiết 9 REST API Endpoints Của Module 6

### 📦 A. Phân Hệ Ordering (`/api/v1/orders`)

| Phương Thức | Đường Dẫn API | Phân Quyền | Tóm Tắt Chức Năng |
|:---:|---|:---:|---|
| `POST` | `/api/v1/orders` | `USER` | Tạo đơn hàng từ phiên giữ chỗ 10 phút & đóng băng giá |
| `GET` | `/api/v1/orders/{id}` | `USER` / `ADMIN` | Lấy thông tin chi tiết đơn hàng theo UUID |
| `GET` | `/api/v1/orders/code/{orderCode}` | `USER` / `ADMIN` | Lấy chi tiết đơn hàng theo mã hiển thị (`ORD-20260822-XXXX`) |
| `GET` | `/api/v1/orders/my-orders` | `USER` | Lấy lịch sử danh sách đơn hàng cá nhân (Phân trang phòng thủ) |
| `POST` | `/api/v1/orders/{id}/cancel` | `USER` / `ADMIN` | Khách hàng chủ động hủy đơn hàng (Nhả vé và mở khóa ghế) |

---

### 💳 B. Phân Hệ Payment (`/api/v1/payments`)

| Phương Thức | Đường Dẫn API | Phân Quyền | Tóm Tắt Chức Năng |
|:---:|---|:---:|---|
| `POST` | `/api/v1/payments/create-url` | `USER` | Khởi tạo phiên thanh toán, ký số và sinh URL cổng (VNPay/MoMo) |
| `GET` | `/api/v1/payments/vnpay/ipn` | `Public` | **Server-to-Server Webhook:** Nhận kết quả thanh toán chốt tiền |
| `GET` | `/api/v1/payments/vnpay/return` | `Public` | **Browser Redirect:** Tiếp nhận khách quay lại sau thanh toán (UI) |
| `GET` | `/api/v1/payments/{id}` | `USER` / `ADMIN` | Tra cứu trạng thái giao dịch thanh toán theo ID |

---

## 🧪 3. Bảng Ma Trận Kiểm Thử Tự Động (Unit Test Matrix)

### 3.1. `OrderServiceTest` (12 Test Cases):
* ✅ `createOrder_Success`: Tạo đơn hàng thành công, tính đúng tổng tiền và đóng băng danh sách vé.
* ✅ `createOrder_ReservationNotFound`: Ném lỗi `RESOURCE_NOT_FOUND` khi ID giữ chỗ không tồn tại.
* ✅ `createOrder_AccessDenied`: Chặn người dùng A cố tình tạo đơn từ phiên giữ chỗ của người dùng B.
* ✅ `createOrder_ReservationExpired`: Chặn tạo đơn khi phiên giữ chỗ đã quá hạn 10 phút.
* ✅ `cancelOrder_Success`: Hủy đơn hàng `PENDING_PAYMENT` thành công và kích hoạt nhả vé ở Reservation.
* ✅ `cancelOrder_InvalidStatus`: Chặn không cho phép hủy đơn hàng đã thanh toán thành công (`PAID`).

### 3.2. `PaymentServiceTest` (11 Test Cases):
* ✅ `createPayment_Success`: Tạo bản ghi Payment `INITIATED`, sinh Pay URL hợp lệ.
* ✅ `handleVNPayIpn_Success`: Xử lý Webhook IPN chuẩn ngân hàng $\rightarrow$ Đổi Order `PAID` và gọi `confirmReservation()`.
* ✅ `handleVNPayIpn_InvalidChecksum`: Bắt và từ chối gói tin có chữ ký băm sai lệch (RspCode `97`).
* ✅ `handleVNPayIpn_Idempotency_AlreadyProcessed`: Bắt và từ chối xử lý trùng lặp khi nhận lại Webhook cũ (RspCode `02`).
* ✅ `handleVNPayIpn_InvalidAmount`: Phát hiện hành vi hacker sửa đổi số tiền giao dịch (RspCode `04`).

---

## 🩺 4. Tổng Hợp Các Lỗi Thực Tế Đã Gặp & Bài Học Kinh Nghiệm (Post-Mortem)

1. **Lỗi `JSONB` vs `String` trong Hibernate 7:**
   * *Nguyên nhân:* Database là kiểu `jsonb`, nhưng Entity khai báo `columnDefinition = "TEXT"`.
   * *Giải pháp:* Dùng annotation `@JdbcTypeCode(SqlTypes.JSON)` và `@Column(columnDefinition = "jsonb")`.
2. **Lỗi Wildcard URL `PathPatternParser`:**
   * *Nguyên nhân:* Spring Boot 3/4 không cho phép dấu `**` nằm ở giữa URL `/payments/**/ipn`.
   * *Giải pháp:* Đổi dấu `**` ở giữa thành dấu `*` đơn lẻ `/payments/*/ipn`.
3. **Lỗi `ObjectMapper` Package trong Spring Boot 4:**
   * *Nguyên nhân:* Jackson 3 trong Spring Boot 4 chuyển package sang `tools.jackson.databind.ObjectMapper`.
   * *Giải pháp:* Import đúng `tools.jackson.databind.ObjectMapper`.
4. **Lỗi Phân Trang `PageRequestUtils.of()`:**
   * *Nguyên nhân:* Gọi hàm 3 tham số thay vì truyền tách biệt `sortBy` và `sortDirection`.
   * *Giải pháp:* Tách RequestParam thành `@RequestParam sortBy` và `@RequestParam sortDirection`.
