package com.smartevent.modules.event.service.impl;

import com.github.slugify.Slugify;
import com.smartevent.common.api.PageResponse;
import com.smartevent.common.enums.EventFileType;
import com.smartevent.common.enums.EventStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.dto.request.CreateEventRequest;
import com.smartevent.modules.event.dto.request.UpdateEventRequest;
import com.smartevent.modules.event.dto.response.CategoryResponse;
import com.smartevent.modules.event.dto.response.EventFileResponse;
import com.smartevent.modules.event.dto.response.EventResponse;
import com.smartevent.modules.event.dto.response.VenueResponse;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventCategory;
import com.smartevent.modules.event.entity.EventFile;
import com.smartevent.modules.event.exception.EventException;
import com.smartevent.modules.event.repository.*;
import com.smartevent.modules.event.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final VenueRepository venueRepository;
    private final EventFileRepository eventFileRepository;
    private final EventCategoryRepository eventCategoryRepository;

    private final Slugify slugify = Slugify.builder().lowerCase(true).build();

    @Override
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


        return toEventResponse(savedEvent);
    }

    @Override
    @Transactional
    public EventResponse updateEvent(UUID eventId, UUID currentUserId, boolean isAdmin, UpdateEventRequest request){

        Event event = eventRepository.findById(eventId).orElseThrow(
                () -> new EventException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy sự kiện")
        );

        /*Chỉ có Admin hoặc chính Organizer đã tạo ra sự kiện mới có quyền chỉnh sửa.*/
        if (!isAdmin && !event.getOrganizerId().equals(currentUserId)) {
            throw new EventException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền chỉnh sửa sự kiện này");
        }

        /*Quy tắc: Sự kiện đã kết thúc (COMPLETED) hoặc đã bị hủy (CANCELLED) thì tuyệt đối không được sửa.*/
        if (event.getStatus() == EventStatus.COMPLETED || event.getStatus() == EventStatus.CANCELLED) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, "Không thể chỉnh sửa sự kiện đã kết thúc hoặc đã bị hủy");
        }

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
        return toEventResponse(updatedEvent);
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getEventBySlug(String slug) {
        Event event = eventRepository.findBySlug(slug)
                .orElseThrow(() -> new EventException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sự kiện"));
        return toEventResponse(event);
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getEventById(UUID id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sự kiện"));
        return toEventResponse(event);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> getPublishedEvents(Pageable pageable) {
        Page<Event> eventPage = eventRepository.findByStatus(EventStatus.PUBLISHED, pageable);

        // Map từng Event thành EventResponse đầy đủ thông qua toEventResponse
        Page<EventResponse> responsePage = eventPage.map(this::toEventResponse);

        return PageResponse.from(responsePage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> getEventsByOrganizer(UUID organizerId, Pageable pageable) {
        Page<Event> eventPage = eventRepository.findByOrganizerId(organizerId, pageable);
        Page<EventResponse> responsePage = eventPage.map(this::toEventResponse);
        return PageResponse.from(responsePage);
    }

    @Override
    @Transactional
    public EventResponse submitForApproval(UUID eventId, UUID currentUserId, boolean isAdmin) {

        /*Kiểm tra sự kiện*/
        Event event = eventRepository.findById(eventId).orElseThrow(
                () -> new EventException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy sự kiện")
        );

        /*Kiểm tra quyền*/
        if (!isAdmin && !event.getOrganizerId().equals(currentUserId)) {
            throw new EventException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền chỉnh sửa sự kiện này");
        }

        /*Kiểm tra trạng thái*/
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new EventException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Chỉ sự kiện ở trạng thái Nháp mới có thể gửi phê duyệt"
            );
        }

        /*Kiểm tra xem có đủ điều kiện để duyệt không?*/
        if (event.getVenueId() == null) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, "Sự kiện phải có địa điểm trước khi gửi duyệt");
        }
        List<EventCategory> eventCategories = eventCategoryRepository.findByIdEventId(event.getId());

        if (eventCategories.isEmpty()) {
            throw new EventException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Sự kiện phải thuộc ít nhất một danh mục"
            );
        }

        /*Lưu trạng thái mới*/
        event.setStatus(EventStatus.PENDING_APPROVAL);

        Event saved = eventRepository.save(event);
        return toEventResponse(saved);
    }

    @Override
    @Transactional
    public EventResponse approveEvent(UUID eventId) {
        /*Kiểm tra sự kiện*/
        Event event = eventRepository.findById(eventId).orElseThrow(
                () -> new EventException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy sự kiện")
        );

        /*Kiểm tra trạng thái*/
        if (event.getStatus() != EventStatus.PENDING_APPROVAL) {
            throw new EventException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Chỉ sự kiện đang chờ duyệt mới có thể phê duyệt"
            );
        }

        /* Kiểm tra trùng lịch địa điểm */
        if (event.getVenueId() != null) {
            boolean hasConflict = eventRepository.hasVenueTimeConflict(
                    event.getVenueId(),
                    event.getStartTime(),
                    event.getEndTime(),
                    eventId
            );
            if (hasConflict) {
                throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, "Địa điểm đã có sự kiện khác diễn ra trong khoảng thời gian này");
            }
        }

        event.setStatus(EventStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());

        Event saved = eventRepository.save(event);
        return toEventResponse(saved);
    }

    @Override
    @Transactional
    public EventResponse rejectEvent(UUID eventId, String reason) {
        /*Kiểm tra sự kiện*/
        Event event = eventRepository.findById(eventId).orElseThrow(
                () -> new EventException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy sự kiện")
        );
        /*Kiểm tra trạng thái*/
        if (event.getStatus() != EventStatus.PENDING_APPROVAL) {
            throw new EventException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Chỉ sự kiện đang chờ duyệt mới có thể từ chối phê duyệt"
            );
        }

        // 3. Chuyển trạng thái về DRAFT để Organizer chỉnh sửa lại
        event.setStatus(EventStatus.DRAFT);

        // 4. Ghi log lý do từ chối
        log.info("Event {} bị từ chối phê duyệt. Lý do: {}", eventId, reason);

        // 5. Lưu vào database và chuyển đổi dữ liệu để trả về
        Event saved = eventRepository.save(event);
        return toEventResponse(saved);
    }

    @Override
    @Transactional
    public EventResponse cancelEvent(UUID eventId, UUID currentUserId, boolean isAdmin, String reason) {
        /*Kiểm tra sự kiện*/
        Event event = eventRepository.findById(eventId).orElseThrow(
                () -> new EventException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy sự kiện")
        );

        /*Kiểm tra quyền*/
        if (!isAdmin && !event.getOrganizerId().equals(currentUserId)) {
            throw new EventException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền chỉnh sửa sự kiện này");
        }

        if (event.getStatus() == EventStatus.COMPLETED || event.getStatus() == EventStatus.CANCELLED) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, "Trạng thái sự kiện hiện tại không cho phép thực hiện thao tác này");
        }

        event.setStatus(EventStatus.CANCELLED);
        log.warn("Event {} đã bị hủy. Lý do: {}", eventId, reason);

        Event saved = eventRepository.save(event);
        return toEventResponse(saved);
    }

    private EventResponse toEventResponse(Event event) {

        /*Lấy Địa điểm*/
        VenueResponse venueResponse = Optional.ofNullable(event.getVenueId())
                .flatMap(venueRepository::findById)
                .map(VenueResponse::from)
                .orElse(null);


        /*Lấy danh mục*/
        List<EventCategory> eventCategories = eventCategoryRepository.findByIdEventId(event.getId());

        List<UUID> categoryIds = eventCategories.stream()
                .map(ec -> ec.getId().getCategoryId())              // ✅ Lấy id phức hợp rồi lấy categoryId
                .toList();

        List<CategoryResponse> categoryResponses = categoryRepository.findAllById(categoryIds).stream()
                .map(CategoryResponse::from)
                .toList();


        /* Lấy Files đính kèm (Banner, Gallery...) */
        List<EventFileResponse> fileResponses = eventFileRepository
                .findByEventIdOrderBySortOrderAsc(event.getId())
                .stream()
                .map(EventFileResponse::from)
                .toList();

        /* Trả về EventResponse đầy đủ */
        return EventResponse.of(event, venueResponse, categoryResponses, fileResponses);
    }

}

