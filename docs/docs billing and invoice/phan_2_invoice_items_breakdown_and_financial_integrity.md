# 📊 MODULE 8 - PHẦN 2: BÓC TÁCH DÒNG HÓA ĐƠN & ĐÓNG BĂNG DỮ LIỆU LỊCH SỬ
## (LINE-ITEM INVOICING, HISTORICAL SNAPSHOTTING & DISCREPANCY PREVENTION)

**Trạng thái:** Hoàn thành 100% · Production Ready  
**Tác giả / Hệ thống:** Backend Engineering Team · Smart Event Ticketing Platform  

---

## 📖 1. Từ Điển Thuật Ngữ Nghiệp Vụ Bóc Tách Dòng Chứng Từ

| Thuật Ngữ | Tên Tiếng Việt | Ý Nghĩa Chuyên Sâu Trong Thiết Kế Hệ Thống |
|---|---|---|
| **Line-Item Invoicing** | Bóc Tách Từng Dòng Hóa Đơn | Kỹ thuật chia nhỏ hóa đơn thành các dòng độc lập (`invoice_items`), mỗi dòng ghi rõ tên hàng hóa, số lượng, đơn giá và thành tiền tương ứng. |
| **Historical Snapshotting** | Chụp Ảnh Đóng Băng Lịch Sử | Lưu trữ trực tiếp chuỗi văn bản mô tả (VD: `"Vé VIP - Concert Mỹ Tâm (Hàng A - Ghế 12)"`) thay vì chỉ lưu ID khóa ngoại. |
| **Audit Trail (Kiểm Toán)** | Dấu Vết Kiểm Toán Thuế | Khả năng giải trình minh bạch cho cơ quan thuế 5 năm sau mà dữ liệu hóa đơn vẫn nguyên vẹn như lúc xuất. |

---

## 🧠 2. Tại Sao Không Dùng Câu Lệnh `JOIN` Mà Phải Đóng Băng Chuỗi `description`?

Đây là **bài học thiết kế kiến trúc kinh điển** trong các hệ thống thương mại điện tử lớn:

```
 ┌────────────────────────────────────────────────────────────────────────────────────────┐
 │                 TẠI SAO PHẢI LƯU `description` TRỰC TIẾP VÀO `invoice_items`?          │
 ├────────────────────────────────────────────────────────────────────────────────────────┤
 │  • Tình huống thực tế:                                                                 │
 │    1. Ngày 01/01/2026: Khách mua "Vé VIP Diamond" giá 2.000.000 VNĐ.                   │
 │    2. Ngày 01/06/2026: Ban tổ chức đổi tên loại vé thành "Vé Siêu VIP" và sửa giá.     │
 │    3. Nếu bảng `invoice_items` KHÔNG lưu `description` mà chỉ `JOIN` sang `ticket_types`│
 │       ──► Hóa đơn cũ của khách sẽ bị biến dạng thành "Vé Siêu VIP"! (SAI PHÁP LUẬT THUẾ)│
 │                                                                                        │
 │  👉 GIẢI PHÁP:                                                                         │
 │  Khi xuất hóa đơn, hệ thống "chụp ảnh" và đóng băng chuỗi:                             │
 │  `"Vé VIP Diamond - Concert Mỹ Tâm (Ghế: A-12)"` vĩnh viễn vào cột `description`!      │
 └────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🗃️ 3. Lược Đồ Bảng Database Flyway V10 (`invoice_items`)

```sql
CREATE TABLE invoice_items (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    order_item_id UUID REFERENCES order_items(id) ON DELETE SET NULL,
    description VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(15,2) NOT NULL,
    total_price DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- Ràng buộc logic số lượng và giá tiền hợp lệ
    CONSTRAINT chk_invoice_item_qty CHECK (quantity > 0),
    CONSTRAINT chk_invoice_item_price CHECK (unit_price >= 0 AND total_price >= 0)
);

CREATE INDEX idx_invoice_items_invoice_id ON invoice_items(invoice_id);
```

---

## 📐 4. Công Thức Tính Toán & Ràng Buộc Khớp Số (Reconciliation):

Mỗi dòng hóa đơn phải tuân thủ công thức:
$$\text{total\_price} = \text{unit\_price} \times \text{quantity}$$

Và tổng tiền gốc của hóa đơn (`invoices.subtotal`) luôn bằng tổng thành tiền của tất cả các dòng:
$$\text{invoices.subtotal} = \sum_{i=1}^{n} \text{invoice\_items}[i].\text{total\_price}$$
