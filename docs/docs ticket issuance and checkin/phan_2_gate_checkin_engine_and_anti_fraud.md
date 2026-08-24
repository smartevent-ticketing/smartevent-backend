# 🚪 MODULE 7 - PHẦN 2: ENGINE SOÁT VÉ CHECK-IN TẠI CỔNG & CHỐNG GIAN LẬN
## (GATE CHECK-IN ENGINE, DUPLICATE SCAN GUARD & STADIUM OFFLINE RESILIENCE)

**Trạng thái:** Hoàn thành 100% · Tốc độ xử lý < 30ms  
**Tác giả:** Backend Engineering Team · Smart Event Ticketing Platform  

---

## 🧭 1. Quy Trình Soát Vé Thời Gian Thực Tại Cửa Cổng Sự Kiện

Khi bảo vệ / máy quét (Turnstile Gate Scanner) quét mã QR trên điện thoại của khán giả:

```
 [ Thiết Bị Quét Tại Cổng (Scanner) ] ──► Gửi Request `POST /api/v1/checkin/scan`
                                                │
                                                ▼
 ┌────────────────────────────────────────────────────────────────────────────┐
 │  QUY TRÌNH 5 BƯỚC XỬ LÝ CỦA `CheckinServiceImpl.processCheckin`:           │
 │                                                                            │
 │  1. Phân giải Input (Mã QR động `TCK-QR...` hoặc Mã vé `TCK-...`):         │
 │     • Nếu là QR Token: Kiểm tra DB có tồn tại và Status == `ACTIVE` không. │
 │     • Nếu `REVOKED` ──► Trả về `INVALID` ❌ ("Mã QR đã bị thu hồi!")      │
 │                                                                            │
 │  2. Đối soát sự kiện (Event Mismatch Guard):                               │
 │     • Kiểm tra `ticket.eventId == request.eventId`.                         │
 │     • Nếu khác ──► Trả về `INVALID` ❌ ("Vé không thuộc sự kiện này!")     │
 │                                                                            │
 │  3. Chống quét trùng lặp (Duplicate Scan Guard):                           │
 │     • Nếu `ticket.status == USED`:                                         │
 │       ──► Trả về `DUPLICATE` ⚠️ ("CẢNH BÁO: Vé đã quét lúc 18:30 Cửa A!")  │
 │                                                                            │
 │  4. Kiểm tra trạng thái vé khác (CANCELLED / REFUNDED / TRANSFERRED):       │
 │     • Nếu khác `ISSUED` ──► Trả về `INVALID` ❌ ("Vé đã bị hủy/hoàn tiền") │
 │                                                                            │
 │  5. CHECK-IN THÀNH CÔNG:                                                   │
 │     • Cập nhật `ticket.status = USED`, `usedAt = Instant.now()`.           │
 │     • Ghi log vào bảng `ticket_checkins` (Result: `SUCCESS`).              │
 │     • Trả về `SUCCESS` ✅: Tên khán giả, Vị trí ghế ngồi, Hạng vé.         │
 └────────────────────────────────────────────────────────────────────────────┘
```

---

## 🛡️ 2. Bảng Ma Trận Phản Hồi Khi Quét Vé (Checkin Result Matrix)

| Kết Quả | Mã Trả Về | Trạng Thái Vé Trước Quét | Hành Động Hệ Thống | Thông Báo Trên Màn Hình Máy Quét |
|:---:|:---:|:---:|---|---|
| **SUCCESS** | `SUCCESS` | `ISSUED` | Đổi vé sang `USED`, lưu `usedAt`, ghi log `SUCCESS` | 🟢 **"HỢP LỆ! Mời quý khách vào cửa (Ghế: A-12)"** |
| **DUPLICATE** | `DUPLICATE` | `USED` | Không sửa vé, ghi log `DUPLICATE` | 🔴 **"CẢNH BÁO: Vé đã quét lúc 18:30:15 tại Cửa A!"** |
| **INVALID** | `INVALID` | `CANCELLED` / `REFUNDED` | Ghi log `INVALID` | ⛔ **"Vé đã bị hủy hoặc hoàn tiền!"** |
| **INVALID** | `INVALID` | Khác Event | Ghi log `INVALID` | ⛔ **"Vé không thuộc sự kiện đang diễn ra tại cửa này!"** |
| **INVALID** | `INVALID` | Token `REVOKED` | Ghi log `INVALID` | ⛔ **"Mã QR này đã bị thu hồi do đổi mã hoặc chuyển nhượng!"** |

---

## 🏟️ 3. Giải Pháp Xử Lý Nghẽn Mạng Tại Sân Vận Động (Stadium Offline Resilience)

Khi 40.000 khán giả tập trung tại sân vận động gây nghẽn sóng 4G/5G:

```
 ┌────────────────────────────────────────────────────────────────────────────────────────┐
 │                    KIẾN TRÚC 3 TẦNG BẢO ĐẢM VÀO SÂN KHI MẤT SÓNG DI ĐỘNG               │
 ├────────────────────────────────────────────────────────────────────────────────────────┤
 │  🟢 TẦNG 1 (Client Pre-caching): Vé và ảnh QR được lưu sẵn trong App / Apple Wallet   │
 │     lúc ở nhà. Đến sân mất mạng 4G vẫn mở ra quét bình thường 0 giây!                  │
 │                                                                                        │
 │  🔵 TẦNG 2 (Private Gate Network): Máy quét của bảo vệ nối mạng dây LAN cáp quang      │
 │     hoặc Wi-Fi nội bộ riêng biệt, hoàn toàn cách ly với sóng di động của khán giả.     │
 │                                                                                        │
 │  🟡 TẦNG 3 (Fallback Box Office): Khách hết pin/hỏng máy có thể đọc Mã vé cố định      │
 │     (`TCK-20260822-XXXX`) + CCCD tại quầy Helpdesk để check-in thủ công.              │
 └────────────────────────────────────────────────────────────────────────────────────────┘
```
