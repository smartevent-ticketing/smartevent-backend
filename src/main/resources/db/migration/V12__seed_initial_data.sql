-- ==============================================================================
-- V12: SEED INITIAL DATA (Users, Roles, Categories, Venues)
-- ==============================================================================

-- 1. SEED DEFAULT USERS (Mật khẩu mặc định: 'Admin@123', 'Organizer@123', 'Customer@123')
-- Sử dụng pgcrypto crypt() để sinh chuẩn mã băm BCrypt tương thích 100% với Spring Security

-- 1.1. Tài khoản Quản trị viên (ADMIN)
INSERT INTO users (id, email, password_hash, full_name, phone, status, created_at, updated_at)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'admin@smartevent.com',
    crypt('Admin@123', gen_salt('bf')),
    'Hệ Thống Quản Trị Viên (System Admin)',
    '0901234567',
    'ACTIVE',
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;

-- 1.2. Tài khoản Ban tổ chức (ORGANIZER)
INSERT INTO users (id, email, password_hash, full_name, phone, status, created_at, updated_at)
VALUES (
    'b0000000-0000-0000-0000-000000000002',
    'organizer@smartevent.com',
    crypt('Organizer@123', gen_salt('bf')),
    'Ban Tổ Chức Sự Kiện Mẫu (Sample Organizer)',
    '0908889999',
    'ACTIVE',
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;

-- 1.3. Tài khoản Khách hàng mua vé (CUSTOMER)
INSERT INTO users (id, email, password_hash, full_name, phone, status, created_at, updated_at)
VALUES (
    'c0000000-0000-0000-0000-000000000003',
    'customer@smartevent.com',
    crypt('Customer@123', gen_salt('bf')),
    'Nguyễn Văn Khách Hàng (Demo Buyer)',
    '0912345678',
    'ACTIVE',
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;

-- 2. GÁN ROLES CHO CÁC TÀI KHOẢN KHỞI TẠO
-- Gán quyền ADMIN
INSERT INTO user_roles (id, user_id, role_id, created_at, updated_at)
SELECT gen_random_uuid(), u.id, r.id, NOW(), NOW()
FROM users u, roles r
WHERE u.email = 'admin@smartevent.com' AND r.name = 'ADMIN'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Gán quyền ORGANIZER
INSERT INTO user_roles (id, user_id, role_id, created_at, updated_at)
SELECT gen_random_uuid(), u.id, r.id, NOW(), NOW()
FROM users u, roles r
WHERE u.email = 'organizer@smartevent.com' AND r.name = 'ORGANIZER'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Gán quyền CUSTOMER
INSERT INTO user_roles (id, user_id, role_id, created_at, updated_at)
SELECT gen_random_uuid(), u.id, r.id, NOW(), NOW()
FROM users u, roles r
WHERE u.email = 'customer@smartevent.com' AND r.name = 'CUSTOMER'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- 3. SEED DANH MỤC SỰ KIỆN MẪU (CATEGORIES)
INSERT INTO categories (id, name, slug, description, status, created_at, updated_at)
VALUES
    ('d0000000-0000-0000-0000-000000000001', 'Âm Nhạc & Concert', 'am-nhac-concert', 'Các buổi hòa nhạc, đêm nhạc live concert của các nghệ sĩ hàng đầu', 'ACTIVE', NOW(), NOW()),
    ('d0000000-0000-0000-0000-000000000002', 'Lễ Hội & Festival', 'le-hoi-festival', 'Lễ hội âm nhạc EDM, Countdown, Festival văn hóa nghệ thuật ngoài trời', 'ACTIVE', NOW(), NOW()),
    ('d0000000-0000-0000-0000-000000000003', 'Hội Thảo & Workshop', 'hoi-thao-workshop', 'Hội nghị công nghệ, diễn đàn kinh tế và các lớp học chuyên đề', 'ACTIVE', NOW(), NOW()),
    ('d0000000-0000-0000-0000-000000000004', 'Thể Thao & Esports', 'the-thao-esports', 'Các trận đấu bóng đá quốc gia, giải đấu thể thao điện tử chuyên nghiệp', 'ACTIVE', NOW(), NOW()),
    ('d0000000-0000-0000-0000-000000000005', 'Sân Khấu & Kịch Nghệ', 'san-khau-kich-nghe', 'Vở kịch nói, nhạc kịch Broadway, múa đương đại và hài kịch', 'ACTIVE', NOW(), NOW())
ON CONFLICT (slug) DO NOTHING;

-- 4. SEED ĐỊA ĐIỂM TỔ CHỨC MẪU (VENUES)
INSERT INTO venues (id, name, address, city, latitude, longitude, capacity, status, created_at, updated_at)
VALUES
    (
        'e0000000-0000-0000-0000-000000000001',
        'Sân Vận Động Quốc Gia Mỹ Đình',
        'Đường Lê Đức Thọ, Phường Mỹ Đình 1, Quận Nam Từ Liêm',
        'Hà Nội',
        21.020500,
        105.764000,
        40000,
        'ACTIVE',
        NOW(),
        NOW()
    ),
    (
        'e0000000-0000-0000-0000-000000000002',
        'Trung Tâm Hội Chợ và Triển Lãm Sài Gòn (SECC)',
        '799 Đường Nguyễn Văn Linh, Phường Tân Phú, Quận 7',
        'Hồ Chí Minh',
        10.729800,
        106.721400,
        15000,
        'ACTIVE',
        NOW(),
        NOW()
    )
ON CONFLICT (id) DO NOTHING;
