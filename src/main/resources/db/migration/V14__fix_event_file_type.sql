-- V14: Fix invalid POSTER enum in event_files to BANNER (com.smartevent.common.enums.EventFileType)
UPDATE event_files
SET file_type = 'BANNER'
WHERE file_type = 'POSTER';

