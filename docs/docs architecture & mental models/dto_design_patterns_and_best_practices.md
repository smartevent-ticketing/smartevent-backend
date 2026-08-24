# 📦 CẨM NANG THIẾT KẾ DTO (REQUEST & RESPONSE) CHUẨN DOANH NGHIỆP
## Smart Event Ticketing Platform — Data Transfer Object Design Guide & Best Practices

**Phiên bản:** 1.0  
**Tác giả:** Backend Engineering Team  
**Mục đích:** Hướng dẫn toàn diện về cơ chế, tư duy thiết kế, các lỗ hổng bảo mật cần tránh, và chuẩn mực viết Request / Response DTO trong các hệ thống Spring Boot hiện đại.

---

## 🧭 MỤC LỤC

1. [Bản Chất Của Pattern DTO (Data Transfer Object)](#1-bản-chất-của-pattern-dto-data-transfer-object)
2. [3 Hiểm Họa Khi Không Dùng DTO (Dùng Trực Tiếp Entity)](#2-3-hiểm-họa-khi-không-dùng-dto-dùng-trực-tiếp-entity)
3. [Khung Tư Duy Thiết Kế REQUEST DTO](#3-khung-tư-duy-thiết-kế-request-dto)
4. [Khung Tư Duy Thiết Kế RESPONSE DTO](#4-khung-tư-duy-thiết-kế-response-dto)
5. [Quy Chuẩn Chuyển Đổi: `from()` vs `of()`](#5-quy-chuẩn-chuyển-đổi-from-vs-of)
6. [Phân Loại DTO Theo Nghiệp Vụ (CRUD DTO vs Command DTO)](#6-phân-loại-dto-theo-nghiệp-vụ-crud-dto-vs-command-dto)
7. [Case Studies Thực Tế Trong Dự Án Smart Event](#7-case-studies-thực-tế-trong-dự-án-smart-event)

---

## 🏛️ 1. BẢN CHẤT CỦA PATTERN DTO (DATA TRANSFER OBJECT)

**DTO (Data Transfer Object)** là một mẫu thiết kế kinh điển được Martin Fowler định nghĩa trong cuốn sách *Patterns of Enterprise Application Architecture*.

```
┌─────────────────────────┐                 ┌──────────────────────────┐                 ┌──────────────────────────┐
│   PRESENTATION LAYER    │                 │      SERVICE LAYER       │                 │    PERSISTENCE LAYER     │
│      (Client / UI)      │                 │     (Business Logic)     │                 │   (Database / Tables)    │
└────────────┬────────────┘                 └────────────┬─────────────┘                 └────────────┬─────────────┘
             │                                           │                                            │
             │   1. Gửi dữ liệu (JSON)                   │                                            │
             ├──────────────────────────────────────────>│                                            │
             │      [ CreateEventRequest (DTO) ]         │   2. Validate & Tạo Entity                 │
             │                                           ├───────────────────────────────────────────>│
             │                                           │      [ Event (JPA Entity) ]                │
             │                                           │                                            │
             │                                           │   3. Lưu DB & Trả Entity về                │
             │                                           │<───────────────────────────────────────────┤
             │   4. Chuyển đổi & Trả dữ liệu (JSON)      │                                            │
             │<──────────────────────────────────────────┤                                            │
             │      [ EventResponse (DTO) ]              │                                            │
```

### 💡 Nguyên tắc cốt lõi:
* **Entity:** Phục vụ **Database** (Quan hệ bảng, khóa chính, ràng buộc toàn vẹn).
* **DTO:** Phục vụ **Giao diện & Người dùng** (Những gì cần nhập và những gì cần xem).
* **Tuyệt đối không để Entity rò rỉ ra ngoài Presentation Layer (Controller).**

---

## 🚨 2. BA HIỂM HỌA KHI KHÔNG DÙNG DTO (DÙNG TRỰC TIẾP ENTITY)

---

### 💥 Hiểm Họa 1: Lỗ Hổng Mass Assignment (Over-Posting Attack)

Hãy tưởng tượng hệ thống cho phép người dùng cập nhật thông tin cá nhân. Nếu Controller nhận trực tiếp `User` Entity:

```java
// ❌ CỰC KỲ NGUY HIỂM:
@PutMapping("/profile")
public void updateProfile(@RequestBody User user) {
    userRepository.save(user);
}
```

**Kịch bản tấn công:**
1. Người dùng bình thường chỉ sửa `fullName: "Nguyễn Văn A"`.
2. Nhưng một Hacker mở Postman và cố tình gửi kèm:
   ```json
   {
       "fullName": "Nguyễn Văn A",
       "role": "ROLE_ADMIN",
       "isVerified": true,
       "balance": 999999999
   }
   ```
3. Lệnh `userRepository.save(user)` sẽ tự động ghi đè quyền `ROLE_ADMIN` vào Database $\rightarrow$ **Hệ thống bị chiếm quyền quản trị hoàn toàn!**

👉 **Giải pháp với DTO:** Tạo `UpdateProfileRequest` chỉ gồm duy nhất trường `fullName`. Dù hacker có gửi thêm bao nhiêu trường lạ, Spring cũng sẽ tự động loại bỏ.

---

### 💥 Hiểm Họa 2: Rò Rỉ Dữ Liệu Nhạy Cảm (Information Leakage)

Nếu Controller trả thẳng đối tượng `User` ra ngoài JSON:
```java
// ❌ LỘ MẬT KHẨU:
@GetMapping("/users/{id}")
public User getUser(@PathVariable UUID id) {
    return userRepository.findById(id).orElseThrow();
}
```
* Trường `passwordHash` (chuỗi mã hóa mật khẩu) và `resetPasswordToken` sẽ bị gửi về trình duyệt của khách $\rightarrow$ Hacker có thể thu thập để tấn công brute-force.
* Dùng `UserResponse` chỉ trả về: `{ id, email, fullName }`.

---

### 💥 Hiểm Họa 3: Lỗi Vòng Lặp Vô Tận Khi Serialize JSON (StackOverflowError)

Nếu `Event` chứa `List<EventArea>`, và mỗi `EventArea` lại chứa đối tượng `Event`:
* Khi thư viện Jackson cố gắng chuyển thành chuỗi JSON:
  `Event` $\rightarrow$ gọi `Area` $\rightarrow$ gọi ngược lại `Event` $\rightarrow$ gọi `Area` $\rightarrow$ ... (Lặp vô tận).
* **Kết quả:** Server bị sập vì lỗi `java.lang.StackOverflowError`.

---

## 📥 3. KHUNG TƯ DUY THIẾT KẾ REQUEST DTO

Khi tạo một file Request DTO, hãy tuân thủ **Bộ lọc 3 tầng**:

```
[ TẦNG 1: XÁC ĐỊNH DỮ LIỆU ĐẦU VÀO CẦN THIẾT ]
   Chỉ đưa vào những gì người dùng bắt buộc phải điền hoặc được phép tùy chọn.

[ TẦNG 2: LOẠI BỎ TRIỆT ĐỂ CÁC TRƯỜNG CẤM ]
   ❌ CẤM đưa id (Khóa chính do Server tự sinh bằng UUID).
   ❌ CẤM đưa createdAt, updatedAt (Do Database tự ghi nhận).
   ❌ CẤM đưa status (Phải qua State Machine / Service xử lý).
   ❌ CẤM đưa userId / organizerId (Bắt buộc lấy an toàn từ JWT @CurrentUser).

[ TẦNG 3: GẮN CHẶT BEAN VALIDATION ]
   Chặn đứng dữ liệu rác ngay ở cửa ngõ trước khi vào tầng Service.
```

### 📋 Bảng Tra Cứu Bean Validation Thông Dụng:

| Annotation | Áp dụng cho | Mục đích | Ví dụ |
|---|---|---|---|
| `@NotBlank` | `String` | Không được `null`, không được rỗng `""`, không được toàn khoảng trắng `"   "` | `@NotBlank String name` |
| `@NotNull` | Mọi Object (`Instant`, `UUID`, `Enum`) | Không được `null` | `@NotNull AreaType areaType` |
| `@Positive` | Số (`Integer`, `BigDecimal`) | Phải $> 0$ | `@Positive Integer capacity` |
| `@PositiveOrZero` | Số | Phải $\ge 0$ | `@PositiveOrZero Integer deadline` |
| `@Future` | `Instant`, `LocalDateTime` | Thời gian phải ở trong tương lai | `@Future Instant startTime` |
| `@Size(min, max)` | `String`, `List` | Giới hạn độ dài chuỗi hoặc số phần tử mảng | `@Size(max = 255) String title` |
| `@Email` | `String` | Đúng định dạng email | `@Email String email` |

---

## 📤 4. KHUNG TƯ DUY THIẾT KẾ RESPONSE DTO

Response DTO không chỉ là bản sao của Entity, mà nó là **sản phẩm may đo riêng cho trải nghiệm người dùng (UX)**:

```
                      ┌────────────────────────────────────────┐
                      │    DỮ LIỆU GỐC TỪ DATABASE (Entity)    │
                      │    - id: UUID                          │
                      │    - name: "Khán Đài VIP"              │
                      │    - capacity: 500                     │
                      └───────────────────┬────────────────────┘
                                          │
                                          ▼  LÀM GIÀU DỮ LIỆU (Enrichment)
                      ┌────────────────────────────────────────┐
                      │    TÍNH TOÁN THÊM TỪ CÁC BẢNG KHÁC:    │
                      │    + totalSeatsConfigured: 480 (ghế)   │
                      │    + availableSeats: 320 (ghế trống)   │
                      │    + venueName: "SVĐ Mỹ Đình"          │
                      └───────────────────┬────────────────────┘
                                          │
                                          ▼
                      ┌────────────────────────────────────────┐
                      │      KẾT QUẢ: EventAreaResponse        │
                      └────────────────────────────────────────┘
```

---

## 🔄 5. QUY CHUẨN CHUYỂN ĐỔI: `from()` VS `of()`

Để code ngắn gọn, sạch đẹp và tuân thủ nguyên tắc *Effective Java*, chúng ta sử dụng **Static Factory Methods** ngay trong Record DTO:

### 🔹 Khi nào dùng `from()`? (1-to-1 Mapping)
Dùng khi chỉ chuyển đổi từ **1 đối tượng Entity duy nhất**:

```java
public record EventSeatResponse(UUID id, String rowName, String seatNumber, SeatStatus status) {
    public static EventSeatResponse from(EventSeat seat) {
        return new EventSeatResponse(
                seat.getId(),
                seat.getRowName(),
                seat.getSeatNumber(),
                seat.getStatus()
        );
    }
}
```

### 🔹 Khi nào dùng `of()`? (Composite / Enrichment Mapping)
Dùng khi cần **ghép nhiều nguồn dữ liệu lại với nhau**:

```java
public record EventAreaResponse(UUID id, String name, Integer capacity, long totalSeatsConfigured) {
    public static EventAreaResponse of(EventArea area, long totalSeatsConfigured) {
        return new EventAreaResponse(
                area.getId(),
                area.getName(),
                area.getCapacity(),
                totalSeatsConfigured // Dữ liệu tính toán thêm
        );
    }
}
```

---

## 🎯 6. PHÂN LOẠI DTO: CRUD DTO VS COMMAND DTO

Trong thực tế, DTO không chỉ dùng để tạo/sửa bảng (CRUD), mà còn dùng để mô tả **Hành động nghiệp vụ (Command DTO)**:

### 🔹 CRUD DTO (Thao tác thực thể):
* `EventAreaRequest`: Tạo 1 khán đài.
* `EventSeatRequest`: Tạo 1 ghế đơn lẻ.

### 🔹 Command DTO (Thực thi thuật toán / Hành động phức tạp):
* **`GenerateSeatsRequest`:** Không tương ứng với 1 bảng nào trong DB, mà nó mang tham số điều khiển thuật toán sinh ma trận ghế:
  ```java
  public record GenerateSeatsRequest(
      @NotBlank String fromRow,     // Hàng bắt đầu: "A"
      @NotBlank String toRow,       // Hàng kết thúc: "J"
      @Positive Integer seatsPerRow // Số ghế mỗi hàng: 20
  ) {}
  ```

---

## 🏆 7. TỔNG KẾT BỘ QUY TẮC VÀNG (GOLDEN RULES)

> 1. 🛡️ **Mọi API nhận Body đều phải dùng DTO + `@Valid`.**
> 2. 🔒 **Không bao giờ tin tưởng dữ liệu từ Client (Luôn validate `@NotBlank`, `@Positive`...).**
> 3. 🚫 **Không nhận `id`, `status`, `userId` từ Request Body.**
> 4. 📦 **Sử dụng Java `record` cho tất cả DTOs (Bất biến, không cần getter/setter dài dòng).**
> 5. 🔄 **Dùng `from()` cho ánh xạ 1-1, dùng `of()` cho ánh xạ ghép nhiều nguồn.**
