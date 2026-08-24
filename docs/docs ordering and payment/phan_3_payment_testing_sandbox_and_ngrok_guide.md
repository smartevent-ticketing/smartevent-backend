# 🧪 MODULE 6: TESTING GUIDE, SANDBOX ACCOUNTS & NGROK TUNNEL
## (HƯỚNG DẪN KIỂM THỬ SANDBOX, DANH SÁCH THẺ TEST & CẤU HÌNH NGROK)

**Trạng thái:** Đang triển khai · Tài liệu thực hành  
**Tác giả / Hệ thống:** Backend Engineering Team · Smart Event Ticketing Platform  

---

## 🎯 1. Tổng Quan Về Môi Trường Sandbox

Môi trường Sandbox là một hệ thống thanh toán giả lập cách ly hoàn toàn do các đối tác (VNPay, MoMo, ZaloPay, PayPal) cung cấp:
* Cho phép kiểm thử toàn bộ luồng thanh toán và xuất vé mà **không cần tiền thật hay tài khoản ngân hàng thật**.
* Mô phỏng được tất cả các kịch bản thực tế: Thanh toán thành công, sai mã OTP, thẻ hết tiền, khách bấm hủy giao dịch, lỗi mạng gửi trùng lặp Webhook.

---

## 💳 2. Danh Sách Thẻ Ngân Hàng Test VNPay (Ngân Hàng NCB)

Khi được chuyển hướng sang giao diện thanh toán VNPay Sandbox, chọn phương thức **"Thẻ nội địa và tài khoản ngân hàng"**:

| Trường Thông Tin | Dữ Liệu Test Hợp Lệ (Thành Công) | Ghi Chú |
|---|---|---|
| **Ngân hàng** | **NCB** (Ngân Hàng Quốc Dân) | Chọn icon ngân hàng NCB trên màn hình |
| **Số thẻ** | `9704198526191432198` | Số thẻ test mặc định của VNPay |
| **Tên chủ thẻ** | `NGUYEN VAN A` | Nhập chữ in hoa không dấu |
| **Ngày phát hành** | `07/15` | Tháng 07 năm 2015 |
| **Mã OTP xác thực** | `123456` | Mã OTP cố định trên môi trường Sandbox |

---

## 🔑 3. Cấu Hình Biến Môi Trường Mẫu (`.env.example`)

Toàn bộ các biến cấu hình cho 4 cổng thanh toán được đặt trong file template [`.env.example`](../../.env.example):

```properties
# ==============================================================================
# CỔNG THANH TOÁN VNPAY (Sandbox)
# ==============================================================================
VNPAY_TMN_CODE=YOUR_VNPAY_TMN_CODE
VNPAY_HASH_SECRET=YOUR_VNPAY_HASH_SECRET
VNPAY_PAY_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_RETURN_URL=http://localhost:8080/api/v1/payments/vnpay/return
VNPAY_IPN_URL=http://localhost:8080/api/v1/payments/vnpay/ipn

# ==============================================================================
# VÍ ĐIỆN TỬ MOMO (Sandbox)
# ==============================================================================
MOMO_PARTNER_CODE=MOMO
MOMO_ACCESS_KEY=F8BBA842ECF85
MOMO_SECRET_KEY=K951B6PE1waDMi640xX0huZDAm&8fStatic
MOMO_ENDPOINT=https://test-payment.momo.vn/v2/gateway/api/create
MOMO_RETURN_URL=http://localhost:8080/api/v1/payments/momo/return
MOMO_IPN_URL=http://localhost:8080/api/v1/payments/momo/ipn

# ==============================================================================
# VÍ ĐIỆN TỬ ZALOPAY (Sandbox)
# ==============================================================================
ZALOPAY_APP_ID=2553
ZALOPAY_KEY1=PcY4iZIKrnbtLENg5BQvdEwDuKjvMwdu
ZALOPAY_KEY2=kLtgPl8HHhfvMuY2tcKbgFn9pInnFUba
ZALOPAY_ENDPOINT=https://sb-openapi.zalopay.vn/v2/create
ZALOPAY_CALLBACK_URL=http://localhost:8080/api/v1/payments/zalopay/callback

# ==============================================================================
# CỔNG THANH TOÁN PAYPAL (Sandbox)
# ==============================================================================
PAYPAL_CLIENT_ID=YOUR_PAYPAL_CLIENT_ID
PAYPAL_SECRET_KEY=YOUR_PAYPAL_SECRET_KEY
PAYPAL_BASE_URL=https://api-m.sandbox.paypal.com
PAYPAL_RETURN_URL=http://localhost:8080/api/v1/payments/paypal/success
PAYPAL_CANCEL_URL=http://localhost:8080/api/v1/payments/paypal/cancel
```

---

## 🚇 4. Hướng Dẫn Vận Hành Ngrok Tunnel

### 4.1. Bản Chất Nghiệp Vụ Của Ngrok:
* Khi ứng dụng chạy trên máy cá nhân (`localhost:8080`), Server của VNPay/MoMo ngoài Internet **không thể truy cập vào máy bạn**.
* **Ngrok** mở một đường hầm an toàn (Secure Tunnel) từ Internet (VD: `https://xxxx.ngrok-free.app`) trỏ thẳng vào cổng `localhost:8080` của máy tính bạn.

### 4.2. Các Lệnh Điều Khiển Ngrok Cơ Bản:

```powershell
# 1. Khởi chạy Tunnel trỏ vào cổng Spring Boot 8080
ngrok http 8080

# 2. Dừng Ngrok
Nhấn Ctrl + C trên cửa sổ terminal

# 3. Tắt tiến trình Ngrok chạy ngầm (nếu cần)
taskkill /f /im ngrok.exe
```

> [!TIP]
> **Khi nào mới cần bật Ngrok?**  
> Trong quá trình viết code và chạy Unit Test, chúng ta **chưa cần bật Ngrok**. Chỉ ở bước kiểm thử tích hợp cuối cùng khi muốn test quẹt thẻ thật trên website của VNPay để VNPay gọi Webhook về máy, chúng ta mới cần bật lệnh `ngrok http 8080`.

---

## 📊 5. Bảng Tra Cứu Mã Phản Hồi IPN Chuẩn Của VNPay (`RspCode`)

Khi Server VNPay gọi vào Webhook IPN, hệ thống phải phản hồi đúng format JSON theo chuẩn ngân hàng:

| `RspCode` | `Message` | Ý Nghĩa Nghiệp Vụ | Hành Động Phía Server Ta |
|:---:|---|---|---|
| **`00`** | `Confirm Success` | Xác nhận giao dịch thành công | Đổi Order thành `PAID` và gọi `confirmReservation` |
| **`01`** | `Order not found` | Không tìm thấy đơn hàng | Trả mã lỗi `01` |
| **`02`** | `Order already confirmed` | Đơn hàng đã được xử lý trước đó | Idempotency chặn lại, không xử lý trùng |
| **`04`** | `Invalid Amount` | Số tiền không khớp với đơn | Báo động gian lận và từ chối |
| **`97`** | `Invalid Checksum` | Chữ ký HMAC-SHA512 sai lệch | Từ chối gói tin |
| **`99`** | `Unknown error` | Lỗi hệ thống nội bộ | Ghi log và trả lỗi |
