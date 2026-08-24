# Documentation home

Đây là điểm bắt đầu chính thức cho toàn bộ tài liệu của Smart Event Ticketing Platform. Cấu trúc mới ưu tiên câu hỏi người đọc cần trả lời; các tài liệu module chi tiết trước đây vẫn được giữ nguyên để không làm mất lịch sử và không gãy liên kết.

## Thứ tự ưu tiên nguồn thông tin

Khi tài liệu có khác biệt, sử dụng thứ tự sau:

1. Mã nguồn, migration và cấu hình đang chạy.
2. Tài liệu hiện hành trong `00-overview`, `01-architecture`, `02-operations`, `03-quality`.
3. Tài liệu module chi tiết trong các thư mục `docs ...`.
4. Đặc tả thiết kế `.docx` và `spec_extracted.txt` — thể hiện mục tiêu/roadmap, không phải mọi phần đều đã triển khai.

## Lộ trình đọc nhanh

### Đánh giá đồ án

1. [Trạng thái Phase 1](00-overview/phase-1-status.md)
2. [Kiến trúc hệ thống](01-architecture/system-architecture.md)
3. [Luồng giao dịch trọng yếu](01-architecture/critical-flows.md)
4. [Chiến lược kiểm thử](03-quality/test-strategy.md)
5. [Giới hạn và backlog kỹ thuật](03-quality/known-limitations.md)

### Chạy và demo

1. [Local development](02-operations/local-development.md)
2. [Runbook vận hành và khôi phục](02-operations/reliability-runbook.md)
3. Swagger UI tại `/swagger-ui.html`

### Hiểu lý do thiết kế

1. [Kiến trúc hệ thống](01-architecture/system-architecture.md)
2. [Architecture Decision Records](01-architecture/architecture-decisions.md)
3. [Luồng giao dịch trọng yếu](01-architecture/critical-flows.md)

## Cấu trúc tài liệu hiện hành

```text
docs/
├── README.md
├── MASTER_DOCUMENTATION_INDEX.md
├── 00-overview/
│   └── phase-1-status.md
├── 01-architecture/
│   ├── system-architecture.md
│   ├── critical-flows.md
│   └── architecture-decisions.md
├── 02-operations/
│   ├── local-development.md
│   └── reliability-runbook.md
├── 03-quality/
│   ├── test-strategy.md
│   └── known-limitations.md
├── schema/
│   └── database_schema_dictionary.md
└── docs <module>/
    └── tài liệu triển khai chi tiết theo phân hệ
```

## Quy ước duy trì

- Không dùng `file:///...`; mọi liên kết nội bộ phải là đường dẫn tương đối.
- Không dùng các từ “exactly-once”, “zero-loss”, “production-ready” nếu chưa nêu rõ điều kiện và bằng chứng.
- Mọi thay đổi state machine, transaction boundary, queue topology hoặc quyền truy cập phải cập nhật tài liệu kiến trúc tương ứng.
- Số endpoint/test/migration là snapshot có ngày, không phải hằng số vĩnh viễn.
- Secret thật không được đưa vào Markdown, `.env.example` hoặc cấu hình test.
- Tài liệu module giải thích “cách triển khai”; ADR giải thích “vì sao chọn”; runbook giải thích “xử lý khi có lỗi”.

Danh mục toàn bộ tài liệu cũ và mới nằm tại [MASTER_DOCUMENTATION_INDEX.md](MASTER_DOCUMENTATION_INDEX.md).

