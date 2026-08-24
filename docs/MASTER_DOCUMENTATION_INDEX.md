# Master documentation index

Điểm bắt đầu khuyến nghị: [Documentation home](README.md). Index này liệt kê cả tài liệu hiện hành và tài liệu module chi tiết/legacy.

## 1. Tài liệu hiện hành

### Tổng quan

- [Trạng thái Phase 1](00-overview/phase-1-status.md)
- [Root README](../README.md)

### Kiến trúc

- [Kiến trúc hệ thống](01-architecture/system-architecture.md)
- [Các luồng giao dịch trọng yếu](01-architecture/critical-flows.md)
- [Architecture Decision Records](01-architecture/architecture-decisions.md)

### Vận hành

- [Local development](02-operations/local-development.md)
- [Reliability runbook](02-operations/reliability-runbook.md)

### Chất lượng

- [Chiến lược kiểm thử Phase 1](03-quality/test-strategy.md)
- [Giới hạn và backlog kỹ thuật](03-quality/known-limitations.md)

### Data/reference

- [Database schema dictionary](schema/database_schema_dictionary.md)
- [Enum catalog](<docs setup enums/enum_catalog.md>)
- [Error logbook](<docs fix lỗi/smart-event-ticketing-error-logbook.md>)

## 2. Đặc tả nguồn

- [System Design & Implementation Specification v1.2 final](<Smart Event Ticketing Platform - System Design & Implementation Specification v1.2 final.docx>)
- [Bản text trích xuất từ đặc tả](spec_extracted.txt)

Đặc tả là mục tiêu thiết kế và roadmap. Khi đánh giá phần đã chạy, ưu tiên mã nguồn và [trạng thái Phase 1](00-overview/phase-1-status.md).

## 3. Tài liệu kiến trúc và mental models

- [Master engineering solutions and architecture patterns](<docs architecture & mental models/master_engineering_solutions_and_architecture_patterns.md>)
- [Zero-to-one backend và high-concurrency roadmap](<docs architecture & mental models/zero_to_one_backend_inception_and_high_concurrency_roadmap.md>)
- [Redis vs PostgreSQL high-concurrency architecture](<docs architecture & mental models/redis_vs_postgresql_high_concurrency_architecture.md>)
- [Time-bound lease và reservation pipeline](<docs architecture & mental models/time_bound_lease_and_reservation_pipeline_architecture.md>)
- [Common layer architecture](<docs architecture & mental models/common_layer_architecture_and_reuse_guide.md>)
- [DTO design patterns](<docs architecture & mental models/dto_design_patterns_and_best_practices.md>)
- [Service layer design](<docs architecture & mental models/service_layer_design_and_mental_model_guide.md>)
- [REST controller design](<docs architecture & mental models/rest_controller_design_patterns_and_pitfalls_guide.md>)
- [Spring Data JPA repository guide](<docs architecture & mental models/spring_data_jpa_repository_design_and_practice_guide.md>)
- [JPA và software architecture best practices](<docs architecture & mental models/jpa_and_software_architecture_best_practices.md>)
- [Setup steps](<docs architecture & mental models/smart-event-ticketing-setup-steps.md>)

Các tài liệu này giải thích kỹ thuật tổng quát; những tuyên bố về semantics/production cần được đối chiếu với [kiến trúc hiện hành](01-architecture/system-architecture.md) và [known limitations](03-quality/known-limitations.md).

## 4. Identity & Access

- [Phần 1 — Dependencies và config](<docs identity/phan_1_dependencies_va_config.md>)
- [Phần 2 — Entities và repositories](<docs identity/phan_2_entities_va_repositories.md>)
- [Phần 3 — Hạ tầng bảo mật JWT](<docs identity/phan_3_ha_tang_bao_mat_jwt.md>)
- [Phần 4 — Logic nghiệp vụ và API](<docs identity/phan_4_logic_nghiep_vu_va_api_endpoints.md>)
- [Hướng dẫn test Postman](<docs identity/huong_dan_test_postman.md>)

## 5. Event & Venue

- [Phần 1 — Category management](<docs event/phan_1_category_management.md>)
- [Phần 2 — Venue management](<docs event/phan_2_venue_management.md>)
- [Phần 3 — Event core management](<docs event/phan_3_event_core_management.md>)
- [Phần 4 — Event areas và seats](<docs event/phan_4_event_areas_and_seats_management.md>)

## 6. Object Storage

- [Tổng quan kiến trúc MinIO](<docs storage/phan_1_tong_quan_va_kien_truc_storage_minio.md>)

## 7. Ticketing Inventory & Reservation

- [Phần 1 — Ticket types](<docs ticketing/phan_1_ticket_types_management.md>)
- [Phần 2 — Sale phases và rules](<docs ticketing/phan_2_ticket_sale_phases_and_rules.md>)
- [Phần 3 — Inventory counter và atomic locking](<docs ticketing/phan_3_inventory_counter_and_atomic_locking.md>)
- [Phần 4 — User counter và anti-scalping](<docs ticketing/phan_4_user_sale_phase_counter_and_anti_scalping.md>)
- [Phần 5 — Reservation pipeline](<docs ticketing/phan_5_reservation_and_realtime_booking_pipeline.md>)

## 8. Ordering & Payment

- [Phần 1 — Ordering và price snapshot](<docs ordering and payment/phan_1_ordering_management_and_snapshot_pipeline.md>)
- [Phần 2 — Payment gateways và security](<docs ordering and payment/phan_2_payment_gateways_and_security_architecture.md>)
- [Phần 3 — Sandbox/ngrok testing](<docs ordering and payment/phan_3_payment_testing_sandbox_and_ngrok_guide.md>)
- [Phần 4 — REST APIs và unit tests](<docs ordering and payment/phan_4_rest_apis_swagger_and_unit_tests_guide.md>)

Luồng late payment hiện hành được mô tả tại [critical flows](01-architecture/critical-flows.md), vì tài liệu module cũ có thể chưa phản ánh compensating flow mới nhất.

## 9. Ticket Issuance & Check-in

- [Phần 1 — Ticket issuance và dynamic QR](<docs ticket issuance and checkin/phan_1_ticket_issuance_and_dynamic_qr_architecture.md>)
- [Phần 2 — Gate check-in và anti-fraud](<docs ticket issuance and checkin/phan_2_gate_checkin_engine_and_anti_fraud.md>)
- [Phần 3 — Ticket transfer và lifecycle](<docs ticket issuance and checkin/phan_3_ticket_transfer_and_lifecycle_management.md>)
- [Phần 4 — REST APIs và unit tests](<docs ticket issuance and checkin/phan_4_ticket_rest_apis_swagger_and_unit_tests.md>)

## 10. Billing & Invoice

- [Phần 1 — Billing/invoice architecture](<docs billing and invoice/phan_1_billing_invoice_architecture_and_vat_compliance.md>)
- [Phần 2 — Invoice items và financial integrity](<docs billing and invoice/phan_2_invoice_items_breakdown_and_financial_integrity.md>)
- [Phần 3 — Async invoice email delivery](<docs billing and invoice/phan_3_async_invoice_email_delivery_and_tracking.md>)
- [Phần 4 — REST APIs và unit tests](<docs billing and invoice/phan_4_invoice_rest_apis_swagger_and_unit_tests.md>)

## 11. Outbox & Messaging

- [Phần 1 — Transactional Outbox](<docs outbox and messaging/phan_1_transactional_outbox_pattern_and_dual_write_solution.md>)
- [Phần 2 — RabbitMQ topology](<docs outbox and messaging/phan_2_rabbitmq_topology_and_routing_architecture.md>)
- [Phần 3 — Event-driven notification](<docs outbox and messaging/phan_3_event_driven_notification_and_async_email_dispatch.md>)
- [Phần 4 — Unit tests và fault tolerance](<docs outbox and messaging/phan_4_outbox_unit_test_suite_and_fault_tolerance.md>)

Semantics chính xác của hệ thống hiện tại là at-least-once; xem [reliability runbook](02-operations/reliability-runbook.md).

## 12. Quy tắc cập nhật index

- Tài liệu mới phải được thêm vào đúng nhóm trong cùng pull request/commit.
- Link phải tương đối và được kiểm tra tồn tại.
- Nếu tài liệu module mâu thuẫn với code, cập nhật tài liệu hiện hành trước và mở backlog sửa tài liệu module.
- Không tăng số lượng tài liệu như một KPI; ưu tiên một nguồn sự thật rõ ràng và ít trùng lặp.
