# 📋 MODULE 7 - PHẦN 4: HỆ THỐNG REST APIS, SWAGGER UI & BỘ UNIT TEST SUITE
## (TICKET REST APIS, OPENAPI SPECS & MOCKITO UNIT TEST MATRIX)

**Trạng thái:** Hoàn thành 100% · 127/127 Tests Passed  
**Tác giả:** Backend Engineering Team · Smart Event Ticketing Platform  

---

## 🧭 1. Tổng Quan 7 REST API Endpoints Của Module 7

### 🎟️ A. Phân Hệ Quản Lý Vé Điện Tử (`/api/v1/tickets`)

| Phương Thức | Đường Dẫn API | Phân Quyền | Tóm Tắt Chức Năng |
|:---:|---|:---:|---|
| `GET` | `/api/v1/tickets/my-tickets` | `USER` | Lấy toàn bộ danh sách vé điện tử trong ví vé của người dùng |
| `GET` | `/api/v1/tickets/{id}` | `USER` / `ADMIN` | Lấy chi tiết vé theo ID kèm hình ảnh mã QR Base64 Data URL |
| `GET` | `/api/v1/tickets/events/{eventId}` | `ORGANIZER` / `ADMIN` | Ban tổ chức / Admin xem toàn bộ vé đã phát hành của sự kiện |
| `POST` | `/api/v1/tickets/{id}/refresh-qr` | `USER` | Làm mới mã QR bảo mật của vé (Thu hồi token cũ, sinh token mới) |
| `POST` | `/api/v1/tickets/{id}/transfer` | `USER` | Chuyển nhượng quyền sở hữu vé sang email người khác |

---

### 🚪 B. Phân Hệ Soát Vé Check-in (`/api/v1/checkin`)

| Phương Thức | Đường Dẫn API | Phân Quyền | Tóm Tắt Chức Năng |
|:---:|---|:---:|---|
| `POST` | `/api/v1/checkin/scan` | `ORGANIZER` / `ADMIN` | Quét soát vé tại cổng (Xác thực mã QR/Mã vé, chống quét trùng) |
| `GET` | `/api/v1/checkin/events/{eventId}/history` | `ORGANIZER` / `ADMIN` | Xem toàn bộ lịch sử các lượt quét vé tại các cổng của sự kiện |

---

## 🧪 2. Bảng Ma Trận Kiểm Thử Tự Động (Unit Test Matrix)

### 2.1. `TicketServiceTest` (4 Test Cases):
* ✅ `issueTickets_Success`: Phát hành vé thành công khi đơn hàng `PAID` (Order có `quantity = 2` sinh 2 vé đơn lẻ kèm 2 QR Token).
* ✅ `getTicketById_Success`: Lấy chi tiết vé thành công kèm chuỗi ảnh QR Code Base64 (`data:image/png;base64,...`).
* ✅ `getTicketById_AccessDenied`: Chặn người dùng lạ xem vé của người khác (Ném lỗi `ACCESS_DENIED`).
* ✅ `refreshTicketQr_Success`: Làm mới mã QR Token bảo mật thành công (Thu hồi token cũ sang `REVOKED` và sinh token mới `ACTIVE`).

### 2.2. `CheckinServiceTest` (4 Test Cases):
* ✅ `checkin_Success`: Quét vé lần đầu thành công $\rightarrow$ Kết quả `SUCCESS`, đổi trạng thái vé sang `USED` và lưu `usedAt`.
* ✅ `checkin_DuplicateScan`: Phát hiện và báo động vé quét trùng (`DUPLICATE`) khi vé đã sử dụng trước đó.
* ✅ `checkin_EventMismatch`: Từ chối check-in khi quét nhầm vé của sự kiện khác (`EVENT_MISMATCH`).
* ✅ `checkin_RevokedQrToken`: Từ chối check-in khi quét phải mã QR Token đã bị thu hồi (`REVOKED`).

---

## 🩺 3. Tổng Hợp Các Lỗi Thực Tế Đã Gặp & Bài Học Kinh Nghiệm (Post-Mortem)

1. **Lỗi Import nhầm `com.smartevent.common.enums.ErrorCode`:**
   * *Nguyên nhân:* `ErrorCode` nằm ở package `com.smartevent.common.error.ErrorCode`.
   * *Giải pháp:* Chuẩn hóa import đúng `com.smartevent.common.error.ErrorCode`.
2. **Thiếu mã lỗi `USER_NOT_FOUND` và `EVENT_NOT_FOUND`:**
   * *Nguyên nhân:* Khi chuyển vé và kiểm tra sự kiện cần tra cứu User và Event.
   * *Giải pháp:* Bổ sung 2 enum vào `ErrorCode.java`.
3. **Cập nhật Constructor `PaymentServiceImpl`:**
   * *Nguyên nhân:* Cần inject `TicketService` để tự động phát hành vé sau khi thanh toán thành công.
   * *Giải pháp:* Bổ sung `TicketService ticketService` vào Constructor của `PaymentServiceImpl` và file test `PaymentServiceTest`.
