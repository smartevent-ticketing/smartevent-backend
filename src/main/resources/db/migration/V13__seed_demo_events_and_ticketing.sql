-- ==============================================================================
-- V13: SEED DEMO EVENTS, AREAS, SEATS, TICKET TYPES, SALE PHASES & ORDERS
-- ==============================================================================

-- 1. SEED EVENT BANNER FILES
INSERT INTO files (id, owner_id, bucket_name, object_name, original_name, content_type, file_size, visibility, scan_status, created_at)
VALUES
    (
        '90000000-0000-0000-0000-000000000001',
        'b0000000-0000-0000-0000-000000000002',
        'smartevent-public',
        'events/poster-concert-hoang-hon.jpg',
        'poster-concert-hoang-hon.jpg',
        'image/jpeg',
        524288,
        'PUBLIC',
        'CLEAN',
        NOW()
    ),
    (
        '90000000-0000-0000-0000-000000000002',
        'b0000000-0000-0000-0000-000000000002',
        'smartevent-public',
        'events/poster-tech-innovators.jpg',
        'poster-tech-innovators.jpg',
        'image/jpeg',
        612400,
        'PUBLIC',
        'CLEAN',
        NOW()
    )
ON CONFLICT (id) DO NOTHING;

-- 2. SEED EVENTS (PUBLISHED, PENDING_APPROVAL, DRAFT)
-- 2.1. Sự kiện Live Concert (Đang mở bán công khai - PUBLISHED)
INSERT INTO events (
    id, organizer_id, venue_id, name, slug, description,
    start_time, end_time, status, city, published_at, created_at, updated_at
)
VALUES (
    'f0000000-0000-0000-0000-000000000001',
    'b0000000-0000-0000-0000-000000000002',
    'e0000000-0000-0000-0000-000000000001',
    'LIVESHOW HOÀNG HÔN MÙA THU 2026',
    'liveshow-hoang-hon-mua-thu-2026',
    'Đại nhạc hội live concert ngoài trời quy mô 40.000 khán giả với sự tham gia của các nghệ sĩ hàng đầu Việt Nam và quốc tế.',
    NOW() + INTERVAL '30 days',
    NOW() + INTERVAL '30 days' + INTERVAL '4 hours',
    'PUBLISHED',
    'Hà Nội',
    NOW(),
    NOW(),
    NOW()
) ON CONFLICT (id) DO NOTHING;

-- 2.2. Sự kiện Hội nghị Công nghệ (Đang mở bán công khai - PUBLISHED)
INSERT INTO events (
    id, organizer_id, venue_id, name, slug, description,
    start_time, end_time, status, city, published_at, created_at, updated_at
)
VALUES (
    'f0000000-0000-0000-0000-000000000002',
    'b0000000-0000-0000-0000-000000000002',
    'e0000000-0000-0000-0000-000000000002',
    'HỘI NGHỊ CÔNG NGHỆ TECH INNOVATORS 2026',
    'hoi-nghi-cong-nghe-tech-innovators-2026',
    'Diễn đàn cấp cao cập nhật xu hướng Trí tuệ nhân tạo (AI), Cloud Native và Chuyển đổi số trong ngành bán vé trực tuyến.',
    NOW() + INTERVAL '45 days',
    NOW() + INTERVAL '45 days' + INTERVAL '8 hours',
    'PUBLISHED',
    'Hồ Chí Minh',
    NOW(),
    NOW(),
    NOW()
) ON CONFLICT (id) DO NOTHING;

-- 2.3. Sự kiện Esports (Chờ Admin duyệt - PENDING_APPROVAL)
INSERT INTO events (
    id, organizer_id, venue_id, name, slug, description,
    start_time, end_time, status, city, published_at, created_at, updated_at
)
VALUES (
    'f0000000-0000-0000-0000-000000000003',
    'b0000000-0000-0000-0000-000000000002',
    'e0000000-0000-0000-0000-000000000002',
    'GIẢI ĐẤU VIETNAM ESPORTS CHAMPIONSHIP 2026',
    'giai-dau-vietnam-esports-championship-2026',
    'Vòng chung kết giải đấu thể thao điện tử quy mô quốc gia quy tụ các tuyển thủ chuyên nghiệp tranh cúp vô địch.',
    NOW() + INTERVAL '60 days',
    NOW() + INTERVAL '60 days' + INTERVAL '6 hours',
    'PENDING_APPROVAL',
    'Hồ Chí Minh',
    NULL,
    NOW(),
    NOW()
) ON CONFLICT (id) DO NOTHING;

-- 2.4. Sự kiện Lễ hội Bản nháp (DRAFT cho Organizer kiểm thử chỉnh sửa)
INSERT INTO events (
    id, organizer_id, venue_id, name, slug, description,
    start_time, end_time, status, city, published_at, created_at, updated_at
)
VALUES (
    'f0000000-0000-0000-0000-000000000004',
    'b0000000-0000-0000-0000-000000000002',
    'e0000000-0000-0000-0000-000000000001',
    'FESTIVAL MÙA HÈ KINETIC BEATS 2026',
    'festival-mua-he-kinetic-beats-2026',
    'Lễ hội mùa hè năng động với trải nghiệm âm nhạc và ẩm thực ngoài trời phong phú.',
    NOW() + INTERVAL '90 days',
    NOW() + INTERVAL '90 days' + INTERVAL '10 hours',
    'DRAFT',
    'Hà Nội',
    NULL,
    NOW(),
    NOW()
) ON CONFLICT (id) DO NOTHING;

-- 3. LIÊN KẾT DANH MỤC SỰ KIỆN (EVENT_CATEGORIES)
INSERT INTO event_categories (event_id, category_id)
VALUES
    ('f0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001'), -- Âm nhạc
    ('f0000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000003'), -- Hội thảo
    ('f0000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000004'), -- Esports
    ('f0000000-0000-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000002')  -- Lễ hội
ON CONFLICT (event_id, category_id) DO NOTHING;

-- 4. LIÊN KẾT POSTER SỰ KIỆN (EVENT_FILES)
INSERT INTO event_files (id, event_id, file_id, file_type, sort_order, created_at)
VALUES
    ('91000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000001', '90000000-0000-0000-0000-000000000001', 'POSTER', 0, NOW()),
    ('91000000-0000-0000-0000-000000000002', 'f0000000-0000-0000-0000-000000000002', '90000000-0000-0000-0000-000000000002', 'POSTER', 0, NOW())
ON CONFLICT (id) DO NOTHING;

-- 5. PHƯƠNG THỨC THANH TOÁN SỰ KIỆN (EVENT_PAYMENT_METHODS)
INSERT INTO event_payment_methods (event_id, method, enabled)
VALUES
    ('f0000000-0000-0000-0000-000000000001', 'VNPAY', true),
    ('f0000000-0000-0000-0000-000000000002', 'VNPAY', true),
    ('f0000000-0000-0000-0000-000000000003', 'VNPAY', true),
    ('f0000000-0000-0000-0000-000000000004', 'VNPAY', true)
ON CONFLICT (event_id, method) DO NOTHING;

-- 6. PHÂN KHU SỰ KIỆN (EVENT_AREAS)
INSERT INTO event_areas (id, event_id, name, area_type, capacity, sort_order, description, created_at, updated_at)
VALUES
    -- Phân khu Event 1
    ('10000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000001', 'Khu VIP Khán Đài A', 'SEATED', 300, 1, 'Ghế ngồi đệm da cao cấp có tầm nhìn trực diện sân khấu', NOW(), NOW()),
    ('10000000-0000-0000-0000-000000000002', 'f0000000-0000-0000-0000-000000000002', 'Khu Tiêu Chuẩn B', 'SEATED', 800, 2, 'Ghế ngồi khán đài tầng 2 góc nhìn bao quát toàn cảnh', NOW(), NOW()),
    ('10000000-0000-0000-0000-000000000003', 'f0000000-0000-0000-0000-000000000001', 'Khu Fanzone Đứng', 'STANDING', 1500, 3, 'Khu vực đứng sát sàn diễn sân khấu chính', NOW(), NOW()),
    -- Phân khu Event 2
    ('10000000-0000-0000-0000-000000000004', 'f0000000-0000-0000-0000-000000000002', 'Khán Phòng Hội Nghị Chính', 'SEATED', 500, 1, 'Hội trường máy lạnh chuẩn quốc tế kèm bàn đại biểu', NOW(), NOW()),
    -- Phân khu Event 3
    ('10000000-0000-0000-0000-000000000005', 'f0000000-0000-0000-0000-000000000003', 'Khu Thi Đấu Esports', 'SEATED', 1000, 1, 'Khu ghế khán đài theo dõi màn hình LED thi đấu', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 7. GHẾ NGỒI VẬT LÝ CHO KHU SEATED (EVENT_SEATS)
-- Sinh ghế Hàng A (A-01 đến A-10) cho Khu VIP Khán Đài A
INSERT INTO event_seats (id, event_area_id, row_name, seat_number, label, status, created_at, updated_at)
SELECT
    gen_random_uuid(),
    '10000000-0000-0000-0000-000000000001',
    'A',
    LPAD(s::text, 2, '0'),
    'Hàng A - Ghế ' || LPAD(s::text, 2, '0'),
    'AVAILABLE',
    NOW(),
    NOW()
FROM generate_series(1, 10) s
ON CONFLICT (event_area_id, row_name, seat_number) DO NOTHING;

-- Sinh ghế Hàng B (B-01 đến B-10) cho Khu VIP Khán Đài A
INSERT INTO event_seats (id, event_area_id, row_name, seat_number, label, status, created_at, updated_at)
SELECT
    gen_random_uuid(),
    '10000000-0000-0000-0000-000000000001',
    'B',
    LPAD(s::text, 2, '0'),
    'Hàng B - Ghế ' || LPAD(s::text, 2, '0'),
    'AVAILABLE',
    NOW(),
    NOW()
FROM generate_series(1, 10) s
ON CONFLICT (event_area_id, row_name, seat_number) DO NOTHING;

-- Sinh ghế Hàng C (C-01 đến C-10) cho Khu Tiêu Chuẩn B
INSERT INTO event_seats (id, event_area_id, row_name, seat_number, label, status, created_at, updated_at)
SELECT
    gen_random_uuid(),
    '10000000-0000-0000-0000-000000000002',
    'C',
    LPAD(s::text, 2, '0'),
    'Hàng C - Ghế ' || LPAD(s::text, 2, '0'),
    'AVAILABLE',
    NOW(),
    NOW()
FROM generate_series(1, 10) s
ON CONFLICT (event_area_id, row_name, seat_number) DO NOTHING;

-- 8. CÁC HẠNG VÉ (TICKET_TYPES)
INSERT INTO ticket_types (id, event_id, event_area_id, name, description, status, created_at, updated_at)
VALUES
    -- Hạng vé Event 1
    ('20000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Vé VIP Thảm Đỏ', 'Ghế ngồi trung tâm, kèm quà tặng áo kỷ niệm và đồ uống welcome', 'ACTIVE', NOW(), NOW()),
    ('20000000-0000-0000-0000-000000000002', 'f0000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002', 'Vé Tiêu Chuẩn Khán Đài', 'Ghế ngồi cố định tầng 2 với tầm nhìn bao quát', 'ACTIVE', NOW(), NOW()),
    ('20000000-0000-0000-0000-000000000003', 'f0000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000003', 'Vé Fanzone Đứng Tự Do', 'Khu đứng sát sân khấu với bầu không khí cuồng nhiệt', 'ACTIVE', NOW(), NOW()),
    -- Hạng vé Event 2
    ('20000000-0000-0000-0000-000000000004', 'f0000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000004', 'Vé Đại Biểu Tech Innovators', 'Tham dự trọn gói các phiên thảo luận và teabreak giao lưu doanh nghiệp', 'ACTIVE', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 9. ĐỢT MỞ BÁN VÉ (TICKET_SALE_PHASES)
INSERT INTO ticket_sale_phases (
    id, ticket_type_id, name, price, quantity,
    sale_start_at, sale_end_at, max_per_order, max_per_user, status, created_at, updated_at
)
VALUES
    -- Phase Event 1 - VIP (Đang mở bán)
    (
        '30000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        'Mở Bán Chính Thức (VIP)',
        1500000.00,
        300,
        NOW() - INTERVAL '1 day',
        NOW() + INTERVAL '29 days',
        4,
        8,
        'ACTIVE',
        NOW(),
        NOW()
    ),
    -- Phase Event 1 - Tiêu chuẩn (Đang mở bán)
    (
        '30000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000002',
        'Mở Bán Chính Thức (Standard)',
        650000.00,
        800,
        NOW() - INTERVAL '1 day',
        NOW() + INTERVAL '29 days',
        4,
        8,
        'ACTIVE',
        NOW(),
        NOW()
    ),
    -- Phase Event 1 - Fanzone (Đang mở bán)
    (
        '30000000-0000-0000-0000-000000000003',
        '20000000-0000-0000-0000-000000000003',
        'Mở Bán Fanzone',
        450000.00,
        1500,
        NOW() - INTERVAL '1 day',
        NOW() + INTERVAL '29 days',
        6,
        10,
        'ACTIVE',
        NOW(),
        NOW()
    ),
    -- Phase Event 2 - Đại biểu (Đang mở bán)
    (
        '30000000-0000-0000-0000-000000000004',
        '20000000-0000-0000-0000-000000000004',
        'Đăng Ký Tham Dự Hội Nghị',
        500000.00,
        500,
        NOW() - INTERVAL '1 day',
        NOW() + INTERVAL '44 days',
        5,
        10,
        'ACTIVE',
        NOW(),
        NOW()
    )
ON CONFLICT (id) DO NOTHING;

-- 10. BẢNG ĐẾM TỒN KHO VÉ (INVENTORY_COUNTERS)
INSERT INTO inventory_counters (
    id, event_id, event_area_id, ticket_type_id, sale_phase_id,
    total_quantity, held_quantity, sold_quantity, updated_at
)
VALUES
    (
        '31000000-0000-0000-0000-000000000001',
        'f0000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000001',
        300, 0, 1, NOW()
    ),
    (
        '31000000-0000-0000-0000-000000000002',
        'f0000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000002',
        '30000000-0000-0000-0000-000000000002',
        800, 0, 0, NOW()
    ),
    (
        '31000000-0000-0000-0000-000000000003',
        'f0000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000003',
        '20000000-0000-0000-0000-000000000003',
        '30000000-0000-0000-0000-000000000003',
        1500, 0, 0, NOW()
    ),
    (
        '31000000-0000-0000-0000-000000000004',
        'f0000000-0000-0000-0000-000000000002',
        '10000000-0000-0000-0000-000000000004',
        '20000000-0000-0000-0000-000000000004',
        '30000000-0000-0000-0000-000000000004',
        500, 0, 0, NOW()
    )
ON CONFLICT (id) DO NOTHING;

-- 11. ĐƠN HÀNG MẪU (ORDERS & ORDER_ITEMS CHO CUSTOMER DEMO)
-- 11.1. Đơn hàng 1: Đã thanh toán (PAID)
INSERT INTO orders (
    id, user_id, order_code, subtotal, discount_amount, fee_amount, total_amount,
    currency, status, selected_payment_method, created_at, updated_at
)
VALUES (
    '40000000-0000-0000-0000-000000000001',
    'c0000000-0000-0000-0000-000000000003', -- customer@smartevent.com
    'ORD-2026-98234',
    1500000.00,
    0.00,
    0.00,
    1500000.00,
    'VND',
    'PAID',
    'VNPAY',
    NOW() - INTERVAL '2 hours',
    NOW() - INTERVAL '2 hours'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO order_items (
    id, order_id, ticket_type_id, sale_phase_id, quantity, unit_price, total_price, created_at
)
VALUES (
    '50000000-0000-0000-0000-000000000001',
    '40000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000001',
    1,
    1500000.00,
    1500000.00,
    NOW() - INTERVAL '2 hours'
) ON CONFLICT (id) DO NOTHING;

-- 11.2. Đơn hàng 2: Đang chờ thanh toán (PENDING_PAYMENT kèm deadline 10 phút để test hủy/thanh toán)
INSERT INTO orders (
    id, user_id, order_code, subtotal, discount_amount, fee_amount, total_amount,
    currency, status, payment_deadline, selected_payment_method, created_at, updated_at
)
VALUES (
    '40000000-0000-0000-0000-000000000002',
    'c0000000-0000-0000-0000-000000000003',
    'ORD-2026-11029',
    650000.00,
    0.00,
    0.00,
    650000.00,
    'VND',
    'PENDING_PAYMENT',
    NOW() + INTERVAL '10 minutes',
    'VNPAY',
    NOW(),
    NOW()
) ON CONFLICT (id) DO NOTHING;

INSERT INTO order_items (
    id, order_id, ticket_type_id, sale_phase_id, quantity, unit_price, total_price, created_at
)
VALUES (
    '50000000-0000-0000-0000-000000000002',
    '40000000-0000-0000-0000-000000000002',
    '20000000-0000-0000-0000-000000000002',
    '30000000-0000-0000-0000-000000000002',
    1,
    650000.00,
    650000.00,
    NOW()
) ON CONFLICT (id) DO NOTHING;

-- 12. GIAO DỊCH THANH TOÁN (PAYMENTS)
INSERT INTO payments (
    id, order_id, payment_method, provider, transaction_id, amount, currency, status, paid_at, created_at, updated_at
)
VALUES (
    '70000000-0000-0000-0000-000000000001',
    '40000000-0000-0000-0000-000000000001',
    'VNPAY',
    'VNPAY',
    'VNPAY_TRANS_98234_OK',
    1500000.00,
    'VND',
    'SUCCESS',
    NOW() - INTERVAL '2 hours',
    NOW() - INTERVAL '2 hours',
    NOW() - INTERVAL '2 hours'
) ON CONFLICT (id) DO NOTHING;

-- 13. VÉ ĐIỆN TỬ ĐÃ PHÁT HÀNH TRONG VÍ (TICKETS & QR TOKENS)
INSERT INTO tickets (
    id, order_item_id, current_owner_user_id, original_buyer_user_id,
    event_id, event_area_id, ticket_type_id, sale_phase_id,
    ticket_code, status, issued_at, created_at, updated_at
)
VALUES (
    '60000000-0000-0000-0000-000000000001',
    '50000000-0000-0000-0000-000000000001',
    'c0000000-0000-0000-0000-000000000003',
    'c0000000-0000-0000-0000-000000000003',
    'f0000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000001',
    'TKT-VIP-2026-001',
    'ISSUED',
    NOW() - INTERVAL '2 hours',
    NOW() - INTERVAL '2 hours',
    NOW() - INTERVAL '2 hours'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO ticket_qr_tokens (
    id, ticket_id, token_hash, status, issued_at
)
VALUES (
    '61000000-0000-0000-0000-000000000001',
    '60000000-0000-0000-0000-000000000001',
    'SMARTEVENT_QR_TKT_VIP_2026_001_VALID',
    'ACTIVE',
    NOW() - INTERVAL '2 hours'
) ON CONFLICT (id) DO NOTHING;

-- 14. HÓA ĐƠN ĐIỆN TỬ GTGT (INVOICES & INVOICE_ITEMS)
INSERT INTO invoices (
    id, order_id, user_id, invoice_code, billing_email, subtotal, total_amount, status, issued_at, created_at, updated_at
)
VALUES (
    '80000000-0000-0000-0000-000000000001',
    '40000000-0000-0000-0000-000000000001',
    'c0000000-0000-0000-0000-000000000003',
    'INV-2026-88991',
    'customer@smartevent.com',
    1500000.00,
    1500000.00,
    'ISSUED',
    NOW() - INTERVAL '2 hours',
    NOW() - INTERVAL '2 hours',
    NOW() - INTERVAL '2 hours'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO invoice_items (
    id, invoice_id, order_item_id, description, quantity, unit_price, total_price, created_at
)
VALUES (
    '81000000-0000-0000-0000-000000000001',
    '80000000-0000-0000-0000-000000000001',
    '50000000-0000-0000-0000-000000000001',
    'Vé VIP Thảm Đỏ - Liveshow Hoàng Hôn Mùa Thu 2026',
    1,
    1500000.00,
    1500000.00,
    NOW() - INTERVAL '2 hours'
) ON CONFLICT (id) DO NOTHING;

