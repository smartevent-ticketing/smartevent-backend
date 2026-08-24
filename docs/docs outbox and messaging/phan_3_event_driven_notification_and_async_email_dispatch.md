# 📧 MODULE 9 - PHẦN 3: PHÂN HỆ THÔNG BÁO HƯỚNG SỰ KIỆN & GỬI EMAIL HTML
## (EVENT-DRIVEN NOTIFICATION CONSUMER & ASYNCHRONOUS HTML DISPATCH)

**Trạng thái:** Hoàn thành 100% · Production Ready  
**Tác giả / Hệ thống:** Backend Engineering Team · Smart Event Ticketing Platform  

---

## 🧭 1. Quy Trình Tiêu Thụ Sự Kiện & Giao Vận Thư (`NotificationEventConsumer`)

Khi tin nhắn đến hàng đợi RabbitMQ, Consumer độc lập sẽ xử lý mà không làm ảnh hưởng đến hiệu năng của luồng thanh toán chính:

```
 [ RabbitMQ Queues ] ──► `@RabbitListener` trong `NotificationEventConsumer`
                                    │
                                    ▼
                     Giải mã chuỗi JSON sang Domain Event:
               • `TicketIssuedEvent` / `InvoiceCreatedEvent`
                                    │
                                    ▼
                     Gọi `EmailService` (Nạp HTML Template):
             • Điền tên sự kiện, mã vé, vị trí ghế, ảnh Base64 QR
             • Điền mã hóa đơn VAT, thời gian xuất, tổng tiền VNĐ
                                    │
                                    ▼
                  Gửi thư qua SMTP / Gmail / SendGrid / SES
```

---

## 🎨 2. Chuẩn Hóa Giao Diện Email HTML Chuyên Nghiệp:
1. **Email Vé Vào Cửa:**
   * Tiêu đề: `🎟️ Vé Điện Tử Cho Sự Kiện: [Tên Sự Kiện] [Mã Vé: TCK-...]`
   * Hiển thị bảng tóm tắt: Tên sự kiện, Vị trí ghế ngồi, Hạng vé.
   * Hiển thị trực tiếp ảnh **Mã QR Base64** ở chính giữa để quét tại cửa soát vé.
2. **Email Hóa Đơn VAT:**
   * Tiêu đề: `🧾 Hóa Đơn Điện Tử Đơn Hàng #[Mã Hóa Đơn] - Smart Event`
   * Hiển thị bảng chi tiết: Mã tra cứu, Thời gian xuất, Tổng tiền định dạng chuẩn Việt Nam (`1.000.000 ₫`).
