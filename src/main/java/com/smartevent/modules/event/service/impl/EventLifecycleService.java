package com.smartevent.modules.event.service.impl;

import com.smartevent.common.enums.EventFileType;
import com.smartevent.common.enums.EventStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.dto.response.EventResponse;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventCategory;
import com.smartevent.modules.event.exception.EventException;
import com.smartevent.modules.event.dto.response.EventSubmissionReadinessResponse;
import com.smartevent.modules.event.repository.EventAreaRepository;
import com.smartevent.modules.event.repository.EventCategoryRepository;
import com.smartevent.modules.event.repository.EventFileRepository;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.service.EventAccessPolicy;
import com.smartevent.modules.ticketing.repository.TicketSalePhaseRepository;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final EventFileRepository eventFileRepository;
    private final EventAreaRepository eventAreaRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketSalePhaseRepository ticketSalePhaseRepository;

    @Transactional(readOnly = true)
    public EventSubmissionReadinessResponse checkSubmissionReadiness(UUID eventId, UUID currentUserId, boolean isAdmin) {
        Event event = eventRepository.findById(eventId).orElseThrow(
                () -> new EventException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sự kiện")
        );

        if (!eventAccessPolicy.canManage(event, currentUserId, isAdmin)) {
            throw new EventException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền chỉnh sửa sự kiện này");
        }

        return evaluateReadiness(event);
    }

    private EventSubmissionReadinessResponse evaluateReadiness(Event event) {
        UUID eventId = event.getId();
        Map<String, Boolean> checklist = new LinkedHashMap<>();
        List<String> blockers = new ArrayList<>();

        // 1. hasBasicInfo
        boolean hasTitle = event.getName() != null && !event.getName().trim().isEmpty();
        boolean hasTime = event.getStartTime() != null && event.getEndTime() != null
                && event.getStartTime().isBefore(event.getEndTime());
        boolean hasBasicInfo = hasTitle && hasTime;
        checklist.put("hasBasicInfo", hasBasicInfo);
        if (!hasBasicInfo) {
            blockers.add("Sự kiện phải có đầy đủ tiêu đề và thời gian bắt đầu trước thời gian kết thúc");
        }

        // 2. hasVenue
        boolean hasVenue = event.getVenueId() != null;
        checklist.put("hasVenue", hasVenue);
        if (!hasVenue) {
            blockers.add("Sự kiện phải có địa điểm trước khi gửi duyệt");
        }

        // 3. hasCategories
        List<EventCategory> eventCategories = eventCategoryRepository.findByIdEventId(eventId);
        boolean hasCategories = !eventCategories.isEmpty();
        checklist.put("hasCategories", hasCategories);
        if (!hasCategories) {
            blockers.add("Sự kiện phải thuộc ít nhất một danh mục");
        }

        // 4. hasBanner
        long bannerCount = eventFileRepository.countByEventIdAndFileType(eventId, EventFileType.BANNER);
        boolean hasBanner = bannerCount > 0;
        checklist.put("hasBanner", hasBanner);
        if (!hasBanner) {
            blockers.add("Sự kiện bắt buộc phải có ảnh Banner trước khi gửi duyệt");
        }

        // 5. hasAreas
        long areaCount = eventAreaRepository.countByEventId(eventId);
        boolean hasAreas = areaCount > 0;
        checklist.put("hasAreas", hasAreas);
        if (!hasAreas) {
            blockers.add("Sự kiện phải có ít nhất một khu vực (Area)");
        }

        // 6. hasTicketTypes
        long ticketTypeCount = ticketTypeRepository.countByEventId(eventId);
        boolean hasTicketTypes = ticketTypeCount > 0;
        checklist.put("hasTicketTypes", hasTicketTypes);
        if (!hasTicketTypes) {
            blockers.add("Sự kiện phải có ít nhất một hạng vé (Ticket Type)");
        }

        // 7. hasSalePhases
        long salePhaseCount = ticketSalePhaseRepository.countByEventId(eventId);
        boolean hasSalePhases = salePhaseCount > 0;
        checklist.put("hasSalePhases", hasSalePhases);
        if (!hasSalePhases) {
            blockers.add("Sự kiện phải có ít nhất một đợt mở bán (Sale Phase)");
        }

        // 8. areaCapacityValid
        boolean areaCapacityValid = true;
        if (hasVenue && hasAreas) {
            try {
                configurationPolicy.validatePublication(event);
            } catch (EventException e) {
                areaCapacityValid = false;
                blockers.add(e.getMessage());
            } catch (Exception e) {
                areaCapacityValid = false;
                blockers.add("Cấu hình sức chứa hoặc số lượng vé không hợp lệ");
            }
        } else {
            areaCapacityValid = false;
        }
        checklist.put("areaCapacityValid", areaCapacityValid);

        // 9. draftStatus
        boolean draftStatus = event.getStatus() == EventStatus.DRAFT;
        checklist.put("draftStatus", draftStatus);
        if (!draftStatus) {
            blockers.add("Chỉ sự kiện ở trạng thái Nháp mới có thể gửi phê duyệt");
        }

        boolean ready = blockers.isEmpty();
        return new EventSubmissionReadinessResponse(ready, checklist, blockers);
    }

    @Transactional
    public EventResponse submitForApproval(UUID eventId, UUID currentUserId, boolean isAdmin) {

        /*Kiểm tra sự kiện với pessimistic write lock (R03)*/
        Event event = eventRepository.findByIdForUpdate(eventId).orElseThrow(
                () -> new EventException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy sự kiện")
        );

        /*Kiểm tra quyền*/
        if (!eventAccessPolicy.canManage(event, currentUserId, isAdmin)) {
            throw new EventException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền chỉnh sửa sự kiện này");
        }

        /*Kiểm tra điều kiện nộp duyệt*/
        EventSubmissionReadinessResponse readiness = evaluateReadiness(event);
        if (!readiness.ready()) {
            String firstBlocker = readiness.blockers().isEmpty()
                    ? "Sự kiện chưa đủ điều kiện gửi duyệt"
                    : readiness.blockers().get(0);
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, firstBlocker);
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
        /*Kiểm tra sự kiện với pessimistic write lock (R03)*/
        Event event = eventRepository.findByIdForUpdate(eventId).orElseThrow(
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
