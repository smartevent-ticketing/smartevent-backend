# 🛡️ PHẦN 4: CHỐNG ĐẦU CƠ & GIỚI HẠN MUA (ANTI-SCALPING & USER PHASE COUNTERS)
## Smart Event Ticketing Platform — Module Ticketing · Sub-module UserSalePhaseCounter

**Ngày hoàn thành:** 19/08/2026  
**Trạng thái:** Hoàn thành 100% · Test Pass 100% · Chặn Đứng Bot & Phe Vé  
**Tác giả / Hệ thống:** Backend Engineering Team  

---

## 🧭 1. Tổng Quan Bài Toán & Vai Trò Nghiệp Vụ

Trong các concert âm nhạc và trận đấu thể thao đỉnh cao, **vấn nạn phe vé (Scalpers / Ticket Bots)** là thách thức sống còn:
* Phe vé sử dụng bot hoặc mở hàng chục tab trình duyệt cùng 1 tài khoản để gửi đồng loạt hàng chục request giữ vé trong cùng 1 mili-giây.
* Mục tiêu: Mua gom số lượng lớn vé nhằm đầu cơ bán lại trên thị trường chợ đen với giá cắt cổ (gấp 300% - 500%).
* **Yêu cầu hệ thống:** Ban tổ chức cấu hình giới hạn số vé tối đa mỗi tài khoản được phép mua trong 1 đợt bán (`max_per_user`, ví dụ: tối đa 2 hoặc 4 vé).

```
                            PHE VÉ GỬI 10 REQUESTS CÙNG 1 MILI-GIÂY
                                              │
                      ┌───────────────────────┴───────────────────────┐
                      ▼                                               ▼
       【 KIỂM TRA ĐƠN GIẢN - THẤT BẠI 】               【 ATOMIC ANTI-SCALPING SHIELD 】
       1. SELECT SUM(qty) FROM orders;                  1. Native UPSERT (đảm bảo row tồn tại)
       2. if (sum + req <= maxPerUser)                  2. Atomic Conditional UPDATE:
              createOrder();                                UPDATE user_sale_phase_counters
       👉 Race condition xảy ra                             SET held = held + :req
       👉 Cả 10 requests đều thấy sum = 0                   WHERE (held + purchased - refunded + :req)
       👉 HẬU QUẢ: Mua trót lọt 10 vé!                             <= :maxPerUser;
                                                        👉 Chỉ requests hợp lệ thành công (updatedRows = 1)
                                                        👉 Vượt trần 4 vé -> Ném lỗi MAX_PER_USER_EXCEEDED!
```

---

## 🗃️ 2. Lược Đồ Database (`user_sale_phase_counters`)

Được định nghĩa trong Flyway Migration [`V5__ticketing_schema.sql`](../../ticketing/src/main/resources/db/migration/V5__ticketing_schema.sql):

```sql
CREATE TABLE user_sale_phase_counters (
    id                 UUID PRIMARY KEY,
    user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sale_phase_id      UUID NOT NULL REFERENCES ticket_sale_phases(id) ON DELETE CASCADE,
    held_quantity      INT NOT NULL DEFAULT 0,
    purchased_quantity INT NOT NULL DEFAULT 0,
    refunded_quantity  INT NOT NULL DEFAULT 0,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, sale_phase_id),
    CONSTRAINT chk_user_counter_held CHECK (held_quantity >= 0),
    CONSTRAINT chk_user_counter_purchased CHECK (purchased_quantity >= 0),
    CONSTRAINT chk_user_counter_refunded CHECK (refunded_quantity >= 0)
);

CREATE INDEX idx_user_phase_counters_user ON user_sale_phase_counters(user_id);
CREATE INDEX idx_user_phase_counters_phase ON user_sale_phase_counters(sale_phase_id);
```

### 🔒 Công Thức Nghiệp Vụ Bất Biến:
$$\mathbf{\text{Effective Occupied Quantity} = \text{held\_quantity} + \text{purchased\_quantity} - \text{refunded\_quantity}}$$

* Điều kiện cho phép giữ thêm $N$ vé:
  $$\mathbf{(\text{held} + \text{purchased} - \text{refunded} + N) \le max\_per\_user}$$

---

## 🏗️ 3. Kiến Trúc Code & Danh Sách File

```
src/main/java/com/smartevent/modules/ticketing/
  ├── entity/
  │     └── UserSalePhaseCounter.java          ← JPA Entity độc lập (id + updatedAt)
  ├── repository/
  │     └── UserSalePhaseCounterRepository.java ← Native UPSERT + Atomic JPQL Update
  ├── dto/
  │     └── response/
  │           └── UserSalePhaseCounterResponse.java ← Response DTO trả về hạn mức của user
  ├── service/
  │     ├── UserSalePhaseCounterService.java    ← Interface nghiệp vụ 5 phương thức
  │     └── impl/
  │           └── UserSalePhaseCounterServiceImpl.java ← Bộ điều phối Anti-Scalping
  └── controller/
        └── UserSalePhaseCounterController.java ← REST API tra cứu hạn mức cá nhân

src/test/java/com/smartevent/modules/ticketing/service/
  └── UserSalePhaseCounterServiceTest.java      ← 100% Mockito Unit Tests (10 Test Cases)
```

---

## 📦 4. Chi Tiết Triển Khai Kỹ Thuật (Layer-by-Layer)

### 🔹 4.1. Pattern Native UPSERT (Chống Duplicate Key Exception)
Khi khách hàng lần đầu tiên thao tác với một Đợt mở bán, nếu 5 tab cùng gọi `save()`, Hibernate sẽ ném lỗi `Unique Constraint Violation`.  
Để xử lý triệt để, chúng ta sử dụng **Native UPSERT của PostgreSQL**:

```sql
INSERT INTO user_sale_phase_counters (id, user_id, sale_phase_id, held_quantity, purchased_quantity, refunded_quantity, updated_at)
VALUES (gen_random_uuid(), :userId, :salePhaseId, 0, 0, 0, NOW())
ON CONFLICT (user_id, sale_phase_id) DO NOTHING;
```

---

### 🔹 4.2. Atomic Anti-Scalping Hold Query
Tại tầng Repository, thao tác giữ vé được thực thi nguyên tử (Atomic Execution):

```java
@Modifying
@Query("""
    UPDATE UserSalePhaseCounter uc
    SET uc.heldQuantity = uc.heldQuantity + :quantity,
        uc.updatedAt = :now
    WHERE uc.userId = :userId
      AND uc.salePhaseId = :salePhaseId
      AND (uc.heldQuantity + uc.purchasedQuantity - uc.refundedQuantity + :quantity) <= :maxPerUser
""")
int atomicHoldUserQuantity(
        @Param("userId") UUID userId,
        @Param("salePhaseId") UUID salePhaseId,
        @Param("quantity") int quantity,
        @Param("maxPerUser") int maxPerUser,
        @Param("now") Instant now
);
```
* **Nếu `updatedRows == 1`:** User còn hạn mức $\rightarrow$ Giữ vé thành công.
* **Nếu `updatedRows == 0`:** Đã vượt trần `max_per_user` $\rightarrow$ Tầng Service lập tức ném lỗi `MAX_PER_USER_EXCEEDED`!

---

### 🔹 4.3. Vòng Đời Tồn Kho Của Người Dùng
1. **`releaseUserHeldTickets`:** Giảm `held_quantity` khi hủy đơn hoặc hết hạn 10 phút.
2. **`confirmUserPurchase`:** Chuyển vé từ `held_quantity` sang `purchased_quantity` khi thanh toán thành công.
3. **`processUserRefund`:** Tăng `refunded_quantity` khi vé được hoàn tiền, tự động mở rộng lại hạn mức cho người dùng.

---

## 🌐 5. Danh Sách REST API Endpoints

| HTTP Method | Endpoint | Phân Quyền | Request Params | Mô Tả |
|:---:|---|:---:|---|---|
| `GET` | `/api/v1/sale-phases/{salePhaseId}/my-counter` | `isAuthenticated()` | Path: `salePhaseId` | Lấy thông tin hạn mức mua vé của chính người dùng |

### Ví dụ Response:

**GET `/api/v1/sale-phases/880e8400-e29b-41d4-a716-446655440003/my-counter`**
```json
// Response 200 OK
{
    "success": true,
    "message": "Success",
    "data": {
        "id": "aa0e8400-e29b-41d4-a716-446655440005",
        "userId": "110e8400-e29b-41d4-a716-446655440000",
        "salePhaseId": "880e8400-e29b-41d4-a716-446655440003",
        "heldQuantity": 1,
        "purchasedQuantity": 2,
        "refundedQuantity": 0,
        "effectiveOccupiedQuantity": 3,
        "updatedAt": "2026-08-19T08:30:00Z"
    }
}
```

---

## 🧪 6. Kết Quả Kiểm Thử Unit Test (Mockito 100%)

Toàn bộ **10 Test Cases** trong [`UserSalePhaseCounterServiceTest.java`](../../ticketing/src/test/java/com/smartevent/modules/ticketing/service/UserSalePhaseCounterServiceTest.java) đều đạt kết quả **PASSED**:
- ✅ `getUserCounter_WhenExists_ReturnsResponse`
- ✅ `getUserCounter_WhenNotExists_ReturnsDefaultZero`
- ✅ `holdUserTickets_WhenMaxPerUserNull_SkipsCheck`
- ✅ `holdUserTickets_Success_WhenWithinLimit`
- ✅ `holdUserTickets_ExceedsLimit_ThrowsException`
- ✅ `holdUserTickets_InvalidQuantity_ThrowsException`
- ✅ `releaseUserHeldTickets_Success`
- ✅ `confirmUserPurchase_Success`
- ✅ `confirmUserPurchase_Fail_ThrowsException`
- ✅ `processUserRefund_Success`
