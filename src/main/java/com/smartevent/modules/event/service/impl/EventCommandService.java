package com.smartevent.modules.event.service.impl;

import com.github.slugify.Slugify;
import com.smartevent.common.enums.EventFileType;
import com.smartevent.common.enums.EventStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.dto.request.CreateEventRequest;
import com.smartevent.modules.event.dto.request.UpdateEventRequest;
import com.smartevent.modules.event.dto.response.EventResponse;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventCategory;
import com.smartevent.modules.event.entity.EventFile;
import com.smartevent.modules.event.exception.EventException;
import com.smartevent.modules.event.repository.CategoryRepository;
import com.smartevent.modules.event.repository.EventCategoryRepository;
import com.smartevent.modules.event.repository.EventFileRepository;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.repository.VenueRepository;
import com.smartevent.modules.event.service.EventAccessPolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventCommandService {

    private final EventAccessPolicy eventAccessPolicy;
    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final VenueRepository venueRepository;
    private final EventFileRepository eventFileRepository;
    private final EventCategoryRepository eventCategoryRepository;

    private final Slugify slugify = Slugify.builder().lowerCase(true).build();
    private final EventQueryService eventQueryService;
    private final com.smartevent.modules.event.service.EventConfigurationPolicy configurationPolicy;

    @Transactional
    public EventResponse createEvent(UUID organizerId, CreateEventRequest request) {
        if (!request.startTime().isBefore(request.endTime())) {
            throw new EventException(ErrorCode.VALIDATION_ERROR, "Thời gian bắt đầu phải trước thời gian kết thúc");
        }

        if (request.startTime().isBefore(Instant.now())) {
            throw new EventException(ErrorCode.VALIDATION_ERROR, "Thời gian bắt đầu phải ở trong tương lai");
        }

        if (request.venueId() != null) {
            venueRepository.findById(request.venueId())
                    .orElseThrow(() -> new EventException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy địa điểm"));

            boolean hasConflict = eventRepository.hasVenueTimeConflict(
                    request.venueId(),
                    request.startTime(),
                    request.endTime(),
                    null // Chưa có eventId nên truyền null
            );

            if (hasConflict) {
                throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, "Địa điểm đã có sự kiện khác diễn ra trong khoảng thời gian này");
            }
        }

        String slug = slugify.slugify(request.name());
        if (eventRepository.existsBySlug(slug)) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, "Tên sự kiện hoặc đường dẫn slug đã tồn tại");
        }

        Event event = new Event();
        event.setOrganizerId(organizerId);
        event.setVenueId(request.venueId());
        event.setName(request.name());
        event.setSlug(slug);
        event.setDescription(request.description());
        event.setStartTime(request.startTime());
        event.setEndTime(request.endTime());
        event.setStatus(EventStatus.DRAFT);
        event.setCity(request.city());
        event.setResaleEnabled(Boolean.TRUE.equals(request.resaleEnabled()));
        event.setMaxResalePriceMultiplier(request.maxResalePriceMultiplier());
        event.setResaleDeadlineHoursBefore(request.resaleDeadlineHoursBefore());
        event.setVirtualQueueEnabled(Boolean.TRUE.equals(request.virtualQueueEnabled()));
        event.setQueueBatchSize(request.queueBatchSize() != null ? request.queueBatchSize() : 50);

        Event savedEvent = eventRepository.save(event);

        if (request.categoryIds() != null && !request.categoryIds().isEmpty()) {
            List<EventCategory> eventCategories = request.categoryIds().stream()
                    .map(categoryId -> new EventCategory(savedEvent.getId(), categoryId))
                    .toList();
            eventCategoryRepository.saveAll(eventCategories);
        }

        List<EventFile> eventFiles = new ArrayList<>();

        if (request.bannerFileId() != null) {
            eventFiles.add(new EventFile(savedEvent.getId(), request.bannerFileId(), EventFileType.BANNER, 0));
        }

        if (request.galleryFileIds() != null && !request.galleryFileIds().isEmpty()) {
            for (int i = 0; i < request.galleryFileIds().size(); i++) {
                eventFiles.add(new EventFile(savedEvent.getId(), request.galleryFileIds().get(i), EventFileType.GALLERY, i + 1));
            }
        }

        if (!eventFiles.isEmpty()) {
            eventFileRepository.saveAll(eventFiles);
        }

        return eventQueryService.toResponse(savedEvent);
    }

    @Transactional
    public EventResponse updateEvent(UUID eventId, UUID currentUserId, boolean isAdmin, UpdateEventRequest request){

        Event event = eventRepository.findByIdForUpdate(eventId).orElseThrow(
                () -> new EventException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy sự kiện")
        );

        /*Chỉ có Admin hoặc chính Organizer đã tạo ra sự kiện mới có quyền chỉnh sửa.*/
        if (!eventAccessPolicy.canManage(event, currentUserId, isAdmin)) {
            throw new EventException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền chỉnh sửa sự kiện này");
        }

        /*Quy tắc: Sự kiện đã kết thúc (COMPLETED) hoặc đã bị hủy (CANCELLED) thì tuyệt đối không được sửa.*/
        if (event.getStatus() == EventStatus.COMPLETED || event.getStatus() == EventStatus.CANCELLED) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, "Không thể chỉnh sửa sự kiện đã kết thúc hoặc đã bị hủy");
        }
        configurationPolicy.validateVenueChange(eventId, request.venueId());

        /* Validate thời gian */
        if (!request.startTime().isBefore(request.endTime())) {
            throw new EventException(ErrorCode.VALIDATION_ERROR, "Thời gian bắt đầu phải trước thời gian kết thúc");
        }

        /* Kiểm tra trùng lịch địa điểm */
        if (request.venueId() != null) {
            venueRepository.findById(request.venueId())
                    .orElseThrow(() -> new EventException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy địa điểm"));

            boolean hasConflict = eventRepository.hasVenueTimeConflict(
                    request.venueId(),
                    request.startTime(),
                    request.endTime(),
                    eventId //
            );

            if (hasConflict) {
                throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, "Địa điểm đã có sự kiện khác diễn ra trong khoảng thời gian này");
            }
        }

        String newSlug = slugify.slugify(request.name());
        if (!newSlug.equals(event.getSlug()) && eventRepository.existsBySlug(newSlug)) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, "Tên sự kiện hoặc đường dẫn slug đã tồn tại");
        }

        event.setVenueId(request.venueId());
        event.setName(request.name());
        event.setSlug(newSlug);
        event.setDescription(request.description());
        event.setStartTime(request.startTime());
        event.setEndTime(request.endTime());
        event.setCity(request.city());
        event.setResaleEnabled(Boolean.TRUE.equals(request.resaleEnabled()));
        event.setMaxResalePriceMultiplier(request.maxResalePriceMultiplier());
        event.setResaleDeadlineHoursBefore(request.resaleDeadlineHoursBefore());
        event.setVirtualQueueEnabled(Boolean.TRUE.equals(request.virtualQueueEnabled()));
        event.setQueueBatchSize(request.queueBatchSize() != null ? request.queueBatchSize() : 50);

        // 1. Cập nhật Categories (Xóa liên kết cũ -> Lưu danh sách mới)
        if (request.categoryIds() != null) {
            eventCategoryRepository.deleteByIdEventId(eventId);
            if (!request.categoryIds().isEmpty()) {
                List<EventCategory> newCategories = request.categoryIds().stream()
                        .map(categoryId -> new EventCategory(eventId, categoryId))
                        .toList();
                eventCategoryRepository.saveAll(newCategories);
            }
        }

        // 2. Cập nhật Files đính kèm (nếu có gửi banner hoặc gallery mới)
        if (request.bannerFileId() != null || request.galleryFileIds() != null) {
            eventFileRepository.deleteByEventId(eventId);
            List<EventFile> newFiles = new ArrayList<>();

            if (request.bannerFileId() != null) {
                newFiles.add(new EventFile(eventId, request.bannerFileId(), EventFileType.BANNER, 0));
            }
            if (request.galleryFileIds() != null && !request.galleryFileIds().isEmpty()) {
                for (int i = 0; i < request.galleryFileIds().size(); i++) {
                    newFiles.add(new EventFile(eventId, request.galleryFileIds().get(i), EventFileType.GALLERY, i + 1));
                }
            }
            if (!newFiles.isEmpty()) {
                eventFileRepository.saveAll(newFiles);
            }
        }

        Event updatedEvent = eventRepository.save(event);
        return eventQueryService.toResponse(updatedEvent);
    }
}
