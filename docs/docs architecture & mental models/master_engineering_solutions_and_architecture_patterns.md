# 🏛️ MASTER ARCHITECTURE: BẢN TỔNG HỢP CÁC KỸ THUẬT XỬ LÝ & GIẢI PHÁP CÔNG NGHỆ CỐT LÕI
## (COMPREHENSIVE HIGH-CONCURRENCY, DATA INTEGRITY & DISTRIBUTED ENGINEERING BLUEPRINT)

> **Trạng thái tài liệu:** tài liệu giải thích/legacy. Một số con số hiệu năng và tuyên bố tuyệt đối bên dưới là mục tiêu thiết kế, chưa phải kết quả benchmark. Khi có khác biệt, ưu tiên [kiến trúc hiện hành](../01-architecture/system-architecture.md), [critical flows](../01-architecture/critical-flows.md) và [known limitations](../03-quality/known-limitations.md).

**Hệ thống:** Smart Event Ticketing Platform · Nền Tảng Bán Vé Sự Kiện Quy Mô Lớn  
**Tác giả:** Backend Engineering Team  
**Mục tiêu:** Tài liệu giải trình toàn diện về các giải pháp kỹ thuật, mẫu thiết kế (Design Patterns), cơ chế bảo mật mật mã học, và chiến lược xử lý đồng thời (High-Concurrency) được áp dụng trên toàn bộ hệ thống từ Module 1 đến Module 8.

---

## 🧭 TỔNG QUAN 12 GIẢI PHÁP KỸ THUẬT ĐỈNH CAO TRONG HỆ THỐNG

```
 ┌────────────────────────────────────────────────────────────────────────────────────────┐
 │                    BẢN ĐỒ 12 GIẢI PHÁP KỸ THUẬT & KIẾN TRÚC HỆ THỐNG                   │
 ├────────────────────────────────────────────────────────────────────────────────────────┤
 │  1. ACID Transactions & Ranh giới @Transactional trong Spring Data JPA                 │
 │  2. Redis Atomic Operations & Bộ nhớ In-Memory Counter chống Overselling               │
 │  3. Pessimistic vs Optimistic Locking trong tranh chấp đặt vé                          │
 │  4. Time-Bound Lease Pattern & Background Sweeper Worker (Giữ chỗ 10 phút)             │
 │  5. Price Snapshot Immutability (Đóng băng giá trị & Chống Price Tampering Attack)    │
 │  6. Strategy Design Pattern cho Đa Cổng Thanh Toán (VNPay, MoMo, ZaloPay, PayPal)     │
 │  7. Cryptographic Signature (HMAC-SHA512/SHA256) & Alphabetical Canonical Sort         │
 │  8. Idempotency Engine & Deduplication (Chống xử lý lặp Webhook Ngân Hàng)             │
 │  9. In-Memory QR Code Generation (Google ZXing Base64 Data URL - 0 Byte Disk Cost)     │
 │  10. Dynamic Rolling QR Token & State Machine Revocation (Chống Phe Vé Bán Trùng)      │
 │  11. Exactly-Once Line-Item Invoicing & Historical Data Snapshotting                   │
 │  12. Defensive Pagination & Spring Security RBAC Token Filtering                       │
 └────────────────────────────────────────────────────────────────────────────────────────┘
```

---

# 1️⃣ ACID TRANSACTIONS & RANH GIỚI GIAO DỊCH DỮ LIỆU

### 🎯 Vấn Đề:
Khi khách mua vé hoặc thanh toán, có 5 hành động xảy ra đồng thời: (1) Cập nhật trạng thái Order sang `PAID`, (2) Đổi ghế sang `SOLD`, (3) Trừ tồn kho, (4) Phát hành vé `Ticket`, (5) Xuất hóa đơn `Invoice`. Nếu bước 4 bị lỗi mạng hoặc sập nguồn máy chủ, mà bước 1 đã đổi sang `PAID` $\rightarrow$ Khách mất tiền mà không có vé!

### 💡 Giải Pháp Kỹ Thuật:
* Áp dụng **ACID Transactions** thông qua Spring Framework `@Transactional`:
  * **Atomicity (Nguyên tử):** Toàn bộ 5 bước cùng thành công (Commit), hoặc nếu có bất kỳ bước nào ném RuntimeException thì toàn bộ dữ liệu tự động quay về trạng thái ban đầu (Rollback 100%).
  * **Consistency (Nhất quán):** Ràng buộc Database (`CHECK subtotal >= 0`, `CHECK quantity > 0`, `FOREIGN KEY ON DELETE RESTRICT`) không bao giờ bị phá vỡ.
  * **Isolation (Cô lập - Read Committed / Repeatable Read):** Các luồng giao dịch đồng thời không nhìn thấy dữ liệu "rác" (Dirty Read) của nhau.
  * **Durability (Bền vững):** Ghi nhật ký WAL (Write-Ahead Logging) của PostgreSQL đảm bảo dữ liệu không mất ngay cả khi mất điện đột ngột.

---

# 2️⃣ REDIS IN-MEMORY ATOMIC COUNTER & BẢO VỆ OVERSOLD

### 🎯 Vấn Đề:
Khi mở bán vé concert Taylor Swift / BlackPink, 50.000 người cùng bấm nút "Mua vé" trong 1 giây. Nếu tất cả 50.000 request cùng lao vào Database PostgreSQL để `SELECT count(*) ...` và `UPDATE inventory`, Database sẽ bị nghẽn (Connection Pool Starvation) và dẫn đến bán vượt quá số ghế (Overselling).

### 💡 Giải Pháp Kỹ Thuật:
* Sử dụng **Redis In-Memory Key-Value Store**:
  * Tồn kho vé được nạp vào RAM của Redis: `Key: inventory:sale_phase:{id}:available`.
  * Thực thi thao tác giảm nguyên tử **`DECR` / `DECRBY`** hoặc **Lua Script**:
    ```lua
    local stock = tonumber(redis.call('get', KEYS[1]))
    if stock >= tonumber(ARGV[1]) then
        redis.call('decrby', KEYS[1], ARGV[1])
        return 1
    else
        return 0
    end
    ```
  * Tốc độ xử lý: **100.000 ops/giây** với độ trễ **dưới 1 mili-giây** trong RAM, giải tỏa 100% áp lực cho Database chính!

---

# 3️⃣ TIME-BOUND LEASE PATTERN (GIỮ CHỖ 10 PHÚT & BACKGROUND SWEEPER)

### 🎯 Vấn Đề:
Khách hàng chọn ghế xong nhưng bỏ đi ăn cơm, không thanh toán. Nếu giữ ghế mãi mãi thì sự kiện bị "treo vé ảo" (Phantom Tickets), khách khác không thể mua được.

### 💡 Giải Pháp Kỹ Thuật:
* Áp dụng mô hình **Time-Bound Lease Pattern (Hợp đồng thuê chỗ có thời hạn)**:
  * Khi tạo phiên giữ chỗ (`POST /api/v1/reservations`), hệ thống thiết lập: `expires_at = Instant.now().plus(10, ChronoUnit.MINUTES)`.
  * Ghế chuyển sang trạng thái tạm thời `HELD`.
* **Cơ Chế Quét Hết Hạn Tự Động (Background Sweeper Worker):**
  * Class `ReservationExpiryWorker` và `OrderExpiryWorker` chạy nền mỗi **30 giây** bằng `@Scheduled(fixedDelay = 30000)`:
  * Quét các bản ghi có `status == 'PENDING'` và `expires_at < NOW()`.
  * Tự động hoàn trả vé về `AVAILABLE`, mở khóa ghế và chuyển trạng thái đơn hàng sang `EXPIRED`.

---

# 4️⃣ PRICE SNAPSHOT IMMUTABILITY (ĐÓNG BĂNG GIÁ BẤT BIẾN)

### 🎯 Vấn Đề (Tấn Công Sửa Giá - Price Tampering Attack):
Kẻ gian mở trình duyệt, bấm F12 (Inspect Element) hoặc dùng Postman sửa body request: `{"totalAmount": 1000}` cho một chiếc vé giá gốc 2.000.000 VNĐ.

### 💡 Giải Pháp Kỹ Thuật:
* **Không bao giờ tin tưởng Client (Zero-Trust Request Body):**
  * DTO `CreateOrderRequest` chỉ nhận duy nhất `reservationId` và `paymentMethod`, **tuyệt đối không có trường số tiền nào trong Request Body**.
  * Server tự lấy `ReservationItem` trong Database, đọc giá vé gốc do Ban tổ chức quy định, tự tính `subtotal` và nhân số lượng trên Backend.
  * Khi tạo `OrderItem`, đơn giá tại thời điểm mua được "chụp ảnh đóng băng" vào `unit_price` và `total_price`. Dù sau này Ban tổ chức có tăng giá vé thì đơn hàng cũ vẫn giữ nguyên giá gốc.

---

# 5️⃣ STRATEGY DESIGN PATTERN CHO HỆ THỐNG ĐA CỔNG THANH TOÁN

### 🎯 Vấn Đề:
Mỗi cổng thanh toán (VNPay, MoMo, ZaloPay, PayPal) có API, định dạng URL, thuật toán băm và cấu trúc IPN hoàn toàn khác nhau. Nếu viết một đống `if-else` lồng nhau trong Service, code sẽ trở thành "mớ bòng bong" (Spaghetti Code) vi phạm nguyên tắc Open/Closed Principle (SOLID).

### 💡 Giải Pháp Kỹ Thuật:
* Áp dụng **Strategy Design Pattern**:
  * Định nghĩa Interface chung: `PaymentGatewayProvider` với phương thức `createPaymentUrl()`.
  * Tạo các lớp hiện thực hóa độc lập: `VNPayGatewayProvider`, `MoMoGatewayProvider`, `ZaloPayGatewayProvider`, `PayPalGatewayProvider`.
  * Trong `PaymentServiceImpl`, Spring Boot tự động inject toàn bộ `List<PaymentGatewayProvider>` vào một `Map<PaymentMethod, PaymentGatewayProvider>`.
  * Khi người dùng chọn cổng nào, hệ thống lấy Strategy tương ứng ra thực thi:
    ```java
    PaymentGatewayProvider provider = gatewayProviders.get(request.paymentMethod());
    return provider.createPaymentUrl(order, ...);
    ```

---

# 6️⃣ CRYPTOGRAPHIC SIGNATURE & ALPHABETICAL CANONICAL SORT

### 🎯 Vấn Đề:
Làm thế nào để đảm bảo gói tin thanh toán gửi sang VNPay/MoMo và gói tin Webhook IPN gửi về không bị hacker đứng ở giữa can thiệp sửa đổi số tiền (Man-In-The-Middle Attack)?

### 💡 Giải Pháp Kỹ Thuật:
* **Thuật Toán Băm Khóa Mật Mã HMAC-SHA512 / HMAC-SHA256:**
  * Toàn bộ tham số được chuẩn hóa và **sắp xếp theo thứ tự bảng chữ cái alphabet (Canonical Alphabetical Sort)**: `vnp_Amount=...&vnp_Command=...&vnp_TxnRef=...`.
  * Sử dụng khóa bí mật `vnp_HashSecret` do ngân hàng cấp riêng để tạo chuỗi băm `SecureHash`.
  * Khi nhận Webhook IPN, Server tự băm lại toàn bộ dữ liệu nhận được và so sánh với chữ ký gửi kèm. Nếu sai lệch dù chỉ 1 ký tự $\rightarrow$ Từ chối ngay lập tức với mã lỗi `97 - Invalid Checksum`!

---

# 7️⃣ IDEMPOTENCY ENGINE (CHỐNG XỬ LÝ LẶP WEBHOOK NGÂN HÀNG)

### 🎯 Vấn Đề (Mạng Chập Chờn & Gateway Retry):
Khi khách thanh toán thành công, cổng VNPay gửi Webhook IPN sang Server của chúng ta. Nếu mạng lag, VNPay tưởng Server chưa nhận được và sẽ gửi lại Webhook đó **3 đến 5 lần**. Nếu không kiểm soát, hệ thống sẽ cộng tiền 5 lần và xuất 5 tấm vé trùng!

### 💡 Giải Pháp Kỹ Thuật:
* **Cơ Chế Bắt Trùng Bằng Bảng `payment_webhook_events`:**
  * Mỗi sự kiện gửi sang có mã định danh sự kiện duy nhất `provider_event_id` (hoặc băm từ mã giao dịch + số tiền).
  * Bước 1 trong hàm IPN:
    ```java
    if (webhookEventRepository.existsByProviderAndProviderEventId("VNPAY", eventId)) {
        return VNPayIpnResponse.orderAlreadyConfirmed(); // RspCode "02"
    }
    ```
  * Nhờ vậy, chỉ duy nhất request đầu tiên được xử lý, tất cả các request đến sau đều bị chặn lại và trả về kết quả an toàn!

---

# 8️⃣ GOOGLE ZXING IN-MEMORY QR RENDERING (0 BYTE DISK COST)

### 🎯 Vấn Đề:
50.000 vé sự kiện nếu lưu 50.000 file `.png` lên ổ cứng sẽ tốn dung lượng và làm nghẽn I/O server.

### 💡 Giải Pháp Kỹ Thuật:
* Class `QrCodeUtils` sử dụng **Google ZXing** để tính toán ma trận điểm ảnh trực tiếp trong RAM (Heap Memory).
* Chuyển mảng byte PNG nén thành chuỗi **`Base64 Data URL`** (`data:image/png;base64,...`).
* Tốn **0 Byte đĩa cứng**, bộ nhớ tạm chỉ $\approx 25 \text{ KB}$ và giải phóng ngay sau khi response trả về cho Client.
* Áp dụng mức độ sửa lỗi **`ErrorCorrectionLevel.H` (30%)** giúp máy quét đọc được ngay cả khi màn hình điện thoại bị nứt vỡ hoặc chói sáng.

---

# 9️⃣ DYNAMIC ROLLING QR TOKEN (CHỐNG GIAN LẬN CHỤP ẢNH MÀN HÌNH)

### 🎯 Vấn Đề (Phe Vé Lừa Đảo Bán Cho Nhiều Người):
Kẻ lừa đảo mua 1 vé thật, chụp ảnh màn hình gửi bán cho 10 người trên mạng xã hội.

### 💡 Giải Pháp Kỹ Thuật:
* **Giải Pháp 1 - Quét Cổng Độc Quyền (Turnstile Check-in Guard):**
  * Máy quét tại cổng chỉ chấp nhận người đầu tiên quét vé (`SUCCESS` $\rightarrow$ đổi sang `USED`). Người thứ 2 đến sau quét cùng ảnh đó sẽ bị máy quét báo động đỏ **`DUPLICATE SCAN`**.
* **Giải Pháp 2 - Chuyển Nhượng Chính Chủ (In-App Ticket Transfer):**
  * Khi người bán chuyển vé sang Email người mua: Hệ thống **thu hồi toàn bộ mã QR cũ (`REVOKED`)** và cấp mã QR mới mang tên người mua. Kẻ bán vé có giữ 1.000 tấm ảnh cũ cũng không thể qua cổng được nữa!

---

# 🔟 EXACTLY-ONCE LINE-ITEM INVOICING & HISTORICAL SNAPSHOTTING

### 🎯 Vấn Đề (Kê Khai Thuế & Toàn Vẹn Tài Chính):
Nhiều năm sau khi mua vé, nếu Ban tổ chức đổi tên loại vé hoặc sửa giá, hóa đơn cũ của khách hàng có bị biến dạng không?

### 💡 Giải Pháp Kỹ Thuật:
* **Ràng buộc $1:1$ Khóa Duy Nhất (`invoices.order_id UNIQUE`):** Đảm bảo mỗi đơn hàng chỉ xuất duy nhất 1 hóa đơn hợp pháp.
* **Chụp Ảnh Lịch Sử (`invoice_items.description`):** Lưu cứng chuỗi văn bản `"Vé VIP Diamond - Concert Mỹ Tâm (Hàng A - Ghế 12)"` và `unit_price` vào Database. Dữ liệu hóa đơn là **bất biến vĩnh viễn (Immutable)** phục vụ kiểm toán thuế minh bạch.
* **Theo Dõi Giao Vận Email (`invoice_deliveries`):** Lưu vết toàn bộ lịch sử gửi và gửi lại (Resend) hóa đơn kèm mã biên nhận `provider_message_id`.
