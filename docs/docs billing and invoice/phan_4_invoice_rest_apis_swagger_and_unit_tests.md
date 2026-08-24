# 📋 MODULE 8 - PHẦN 4: HỆ THỐNG REST APIS, SWAGGER UI & BỘ UNIT TEST SUITE
## (INVOICE REST APIS, OPENAPI SPECS & MOCKITO UNIT TEST MATRIX)

**Trạng thái:** Hoàn thành 100% · 6/6 Tests Pass Xanh  
**Tác giả / Hệ thống:** Backend Engineering Team · Smart Event Ticketing Platform  

---

## 🧭 1. Chi Tiết 5 REST API Endpoints Của Module 8 (`/api/v1/invoices`)

| Phương Thức | Đường Dẫn API | Phân Quyền | Tóm Tắt Chức Năng |
|:---:|---|:---:|---|
| `GET` | `/api/v1/invoices/my-invoices` | `USER` | Lấy danh sách tất cả các hóa đơn điện tử của người dùng hiện tại |
| `GET` | `/api/v1/invoices/{id}` | `USER` / `ADMIN` | Lấy thông tin chi tiết hóa đơn điện tử theo ID |
| `GET` | `/api/v1/invoices/order/{orderId}` | `USER` / `ADMIN` | Lấy hóa đơn điện tử tương ứng của đơn hàng theo Order ID |
| `GET` | `/api/v1/invoices/code/{invoiceCode}` | `USER` / `ADMIN` | Tra cứu hóa đơn điện tử theo mã hiển thị công khai (VD: `INV-20260822-ABC12345`) |
| `POST` | `/api/v1/invoices/{id}/send-email` | `USER` | Gửi hoặc gửi lại hóa đơn điện tử đính kèm chi tiết qua email của khách |

---

## 🧪 2. Bảng Ma Trận Kiểm Thử Tự Động (Unit Test Matrix - `InvoiceServiceTest`)

| STT | Tên Test Case | Mục Đích Kiểm Thử Nghiệp Vụ | Kết Quả Kỳ Vọng |
|:---:|---|---|:---:|
| **1** | `issueInvoice_Success` | Xuất hóa đơn cho đơn hàng `PAID` | Tạo `Invoice` đóng băng đúng Subtotal, Discount, Fee, Total; lưu `InvoiceItem`s và `InvoiceDelivery`. |
| **2** | `issueInvoice_OrderNotPaid` | Chặn xuất hóa đơn khi đơn chưa trả tiền (`PENDING_PAYMENT`) | Ném lỗi `ORDER_INVALID_STATUS`. |
| **3** | `issueInvoice_AlreadyIssued_ReturnsExisting` | Cơ chế Idempotency chống xuất trùng lặp | Trả về ngay hóa đơn đã tồn tại, không lưu trùng vào Database. |
| **4** | `getInvoiceById_Success_Owner` | Lấy chi tiết hóa đơn theo ID cho chính chủ | Trả về `InvoiceResponse` với danh sách items đầy đủ. |
| **5** | `getInvoiceById_AccessDenied` | Chặn người dùng lạ xem trộm hóa đơn người khác | Ném lỗi `ACCESS_DENIED`. |
| **6** | `sendInvoiceEmail_Success` | Gửi lại hóa đơn sang email tùy chọn | Tạo bản ghi `InvoiceDelivery` với trạng thái `SENT`. |

---

## 🩺 3. Tổng Hợp Các Lỗi Thực Tế Đã Gặp & Bài Học Kinh Nghiệm (Post-Mortem)

1. **Lỗi `Field 'invoiceService' might not have been initialized`:**
   * *Nguyên nhân:* Khai báo `private final InvoiceService invoiceService;` trong `PaymentServiceImpl` nhưng quên thêm vào Constructor parameters và `this.invoiceService = invoiceService;`.
   * *Giải pháp:* Bổ sung tham số vào Constructor và cập nhật `PaymentServiceTest`.
2. **Kế thừa `@Id` và `@UuidGenerator` từ `BaseEntity`:**
   * *Nguyên nhân:* `Invoice` kế thừa `BaseEntity` nên không cần khai báo lại `id`.
   * *Giải pháp:* Sử dụng `@UuidGenerator` trực tiếp cho `InvoiceItem` và `InvoiceDelivery`.
