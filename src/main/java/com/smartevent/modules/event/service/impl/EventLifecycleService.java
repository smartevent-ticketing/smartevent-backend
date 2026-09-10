package com.smartevent.modules.event.service.impl;

import com.smartevent.common.enums.EventStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.dto.response.EventResponse;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventCategory;
import com.smartevent.modules.event.exception.EventException;
import com.smartevent.modules.event.repository.EventCategoryRepository;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.service.EventAccessPolicy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventLifecycleService {

    private final EventAccessPolicy eventAccessPolicy;
    private final EventRepository eventRepository;
    private final EventCategoryRepository eventCategoryRepository;
    private final EventQueryService eventQueryService;
    private final com.smartevent.modules.event.service.EventConfigurationPolicy configurationPolicy;
    private final org.springframework.context.ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public EventResponse submitForApproval(UUID eventId, UUID currentUserId, boolean isAdmin) {

        /*Kiểm tra sự kiện*/
        Event event = eventRepository.findById(eventId).orElseThrow(
                () -> new EventException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy sự kiện")
        );

        /*Kiểm tra quyền*/
        if (!eventAccessPolicy.canManage(event, currentUserId, isAdmin)) {
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
        return eventQueryService.toResponse(saved);
    }

    @Transactional
    public EventResponse approveEvent(UUID eventId) {
        /*Kiểm tra sự kiện*/
        Event event = eventRepository.findByIdForUpdate(eventId).orElseThrow(
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

        configurationPolicy.validatePublication(event);
        event.setStatus(EventStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());

        Event saved = eventRepository.save(event);
        return eventQueryService.toResponse(saved);
    }

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
        return eventQueryService.toResponse(saved);
    }

    @Transactional
    public EventResponse cancelEvent(UUID eventId, UUID currentUserId, boolean isAdmin, String reason) {
        /*Kiểm tra sự kiện*/
        Event event = eventRepository.findByIdForUpdate(eventId).orElseThrow(
                () -> new EventException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy sự kiện")
        );

        /*Kiểm tra quyền*/
        if (!eventAccessPolicy.canManage(event, currentUserId, isAdmin)) {
            throw new EventException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền chỉnh sửa sự kiện này");
        }

        if (event.getStatus() == EventStatus.COMPLETED || event.getStatus() == EventStatus.CANCELLED) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, "Trạng thái sự kiện hiện tại không cho phép thực hiện thao tác này");
        }

        event.setStatus(EventStatus.CANCELLED);
        event.setCancellationReason(reason);
        log.warn("Event {} đã bị hủy. Lý do: {}", eventId, reason);

        Event saved = eventRepository.save(event);
        applicationEventPublisher.publishEvent(new com.smartevent.modules.event.service.EventCancelled(eventId, reason));
        return eventQueryService.toResponse(saved);
    }
}
