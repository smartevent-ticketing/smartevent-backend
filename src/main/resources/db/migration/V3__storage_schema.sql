-- V3: Storage Schema (files metadata & avatar reference)

CREATE TABLE files (
    id UUID PRIMARY KEY,
    owner_id UUID REFERENCES users(id) ON DELETE SET NULL,
    bucket_name VARCHAR(100) NOT NULL,
    object_name VARCHAR(500) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    checksum VARCHAR(64),
    visibility VARCHAR(30) NOT NULL DEFAULT 'PRIVATE',
    scan_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_files_owner_id ON files(owner_id);
CREATE INDEX idx_files_bucket_object ON files(bucket_name, object_name);

-- Thêm avatar_file_id vào bảng users sau khi bảng files đã tồn tại
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_file_id UUID REFERENCES files(id) ON DELETE SET NULL;
