package com.smartevent.modules.event.service.impl;

import com.smartevent.common.enums.EventFileType;
import com.smartevent.common.enums.EventStatus;
import com.smartevent.common.enums.FileVisibility;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.dto.request.UpdateMediaOrderRequest;
import com.smartevent.modules.event.dto.response.EventMediaResponse;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventFile;
import com.smartevent.modules.event.exception.EventException;
import com.smartevent.modules.event.repository.EventFileRepository;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.service.EventAccessPolicy;
import com.smartevent.modules.event.service.EventMediaService;
import com.smartevent.modules.storage.dto.response.FileUploadResponse;
import com.smartevent.modules.storage.dto.response.PresignedUrlResponse;
import com.smartevent.modules.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventMediaServiceImpl implements EventMediaService {

    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB
    private static final int MAX_GALLERY_IMAGES = 8;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final EventRepository eventRepository;
    private final EventFileRepository eventFileRepository;
    private final StorageService storageService;
    private final EventAccessPolicy eventAccessPolicy;

    @Override
    @Transactional
    public EventMediaResponse uploadMedia(UUID eventId, MultipartFile file, EventFileType fileType, UUID currentUserId, boolean isAdmin) {
        Event event = getEventOrThrow(eventId);
        validateManagementAccess(event, currentUserId, isAdmin);
        validateDraftStatus(event);
        validateImageFile(file);

        int sortOrder = 0;
        if (fileType == EventFileType.BANNER) {
            // Quy tắc: 1 Banner duy nhất -> Gỡ bỏ banner cũ nếu có
            List<EventFile> existingBanners = eventFileRepository.findByEventIdAndFileType(eventId, EventFileType.BANNER);
            for (EventFile oldBanner : existingBanners) {
                eventFileRepository.delete(oldBanner);
                try {
                    storageService.deleteFile(oldBanner.getFileId(), currentUserId);
                } catch (Exception e) {
                    log.warn("Không thể xóa file MinIO cũ {}: {}", oldBanner.getFileId(), e.getMessage());
                }
            }
        } else if (fileType == EventFileType.GALLERY) {
            // Quy tắc: Tối đa 8 ảnh gallery
            long galleryCount = eventFileRepository.countByEventIdAndFileType(eventId, EventFileType.GALLERY);
            if (galleryCount >= MAX_GALLERY_IMAGES) {
                throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, "Một sự kiện chỉ được có tối đa " + MAX_GALLERY_IMAGES + " ảnh trong bộ sưu tập (Gallery)");
            }
            sortOrder = (int) galleryCount;
        }

        // Upload lên MinIO qua StorageService
        String folder = "events/" + eventId;
        FileUploadResponse uploadRes = storageService.uploadFile(file, currentUserId, folder, FileVisibility.PUBLIC);

        // Lưu liên kết vào event_files
        EventFile eventFile = new EventFile(eventId, uploadRes.id(), fileType, sortOrder);
        eventFile = eventFileRepository.save(eventFile);
        return new EventMediaResponse(
                eventFile.getId(),
                eventFile.getFileId(),
                eventFile.getFileType(),
                uploadRes.url(),
                eventFile.getSortOrder(),
                eventFile.getCreatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventMediaResponse> getEventMedia(UUID eventId, UUID currentUserId, boolean isAdmin) {
        Event event = getEventOrThrow(eventId);

        // Khách vãng lai chỉ được xem khi event đã PUBLISHED
        boolean isPublished = event.getStatus() == EventStatus.PUBLISHED;
        boolean canManage = currentUserId != null && eventAccessPolicy.canManage(event, currentUserId, isAdmin);

        if (!isPublished && !canManage) {
            throw new EventException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền xem tài nguyên media của sự kiện này");
        }

        List<EventFile> files = eventFileRepository.findByEventIdOrderBySortOrderAsc(eventId);

        return files.stream().map(f -> {
            String url = null;
            try {
                PresignedUrlResponse presigned = storageService.getPresignedUrl(f.getFileId(), currentUserId);
                url = presigned.url();
            } catch (Exception e) {
                log.warn("Không thể sinh presigned url cho fileId {}: {}", f.getFileId(), e.getMessage());
            }
            return new EventMediaResponse(
                    f.getId(),
                    f.getFileId(),
                    f.getFileType(),
                    url,
                    f.getSortOrder(),
                    f.getCreatedAt()
            );
        }).toList();
    }

    @Override
    @Transactional
    public void updateMediaOrder(UUID eventId, UpdateMediaOrderRequest request, UUID currentUserId, boolean isAdmin) {
        Event event = getEventOrThrow(eventId);
        validateManagementAccess(event, currentUserId, isAdmin);
        validateDraftStatus(event);

        for (UpdateMediaOrderRequest.MediaOrderItem item : request.items()) {
            EventFile eventFile = eventFileRepository.findByIdAndEventId(item.eventFileId(), eventId)
                    .orElseThrow(() -> new EventException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy ảnh: " + item.eventFileId()));
            eventFile.setSortOrder(item.sortOrder());
            eventFileRepository.save(eventFile);
        }
    }

    @Override
    @Transactional
    public void deleteMedia(UUID eventId, UUID eventFileId, UUID currentUserId, boolean isAdmin) {
        Event event = getEventOrThrow(eventId);
        validateManagementAccess(event, currentUserId, isAdmin);
        validateDraftStatus(event);

        EventFile eventFile = eventFileRepository.findByIdAndEventId(eventFileId, eventId)
                .orElseThrow(() -> new EventException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy ảnh trong sự kiện này"));

        eventFileRepository.delete(eventFile);
        try {
            storageService.deleteFile(eventFile.getFileId(), currentUserId);
        } catch (Exception e) {
            log.warn("Không thể xóa file MinIO {}: {}", eventFile.getFileId(), e.getMessage());
        }
    }

    private Event getEventOrThrow(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new EventException(ErrorCode.EVENT_NOT_FOUND, "Không tìm thấy sự kiện"));
    }

    private void validateManagementAccess(Event event, UUID currentUserId, boolean isAdmin) {
        if (!eventAccessPolicy.canManage(event, currentUserId, isAdmin)) {
            throw new EventException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền quản lý tài nguyên của sự kiện này");
        }
    }

    private void validateDraftStatus(Event event) {
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, "Chỉ được phép thay đổi ảnh khi sự kiện ở trạng thái DRAFT");
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new EventException(ErrorCode.VALIDATION_ERROR, "Tệp tin tải lên không được để trống");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new EventException(ErrorCode.VALIDATION_ERROR, "Kích thước ảnh vượt quá giới hạn 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new EventException(ErrorCode.VALIDATION_ERROR, "Định dạng file không hỗ trợ. Chỉ chấp nhận JPG, PNG hoặc WebP");
        }
    }
}
