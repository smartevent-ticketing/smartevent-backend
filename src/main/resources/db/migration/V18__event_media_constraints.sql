-- V18: Add unique constraints for event files (single banner per event, unique event-file pair)

-- 1. Đảm bảo mỗi sự kiện chỉ có tối đa 1 ảnh BANNER (dùng Partial Unique Index)
CREATE UNIQUE INDEX IF NOT EXISTS idx_event_files_single_banner
    ON event_files (event_id)
    WHERE file_type = 'BANNER';

-- 2. Đảm bảo 1 file vật lý không bị gắn trùng lặp nhiều lần vào cùng 1 sự kiện
CREATE UNIQUE INDEX IF NOT EXISTS idx_event_files_event_file
    ON event_files (event_id, file_id);
