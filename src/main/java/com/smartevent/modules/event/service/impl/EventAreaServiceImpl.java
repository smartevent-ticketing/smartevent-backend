package com.smartevent.modules.event.service.impl;

import com.smartevent.common.enums.EventStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.dto.request.EventAreaRequest;
import com.smartevent.modules.event.dto.response.EventAreaResponse;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventArea;
import com.smartevent.modules.event.entity.Venue;
import com.smartevent.modules.event.exception.EventException;
import com.smartevent.modules.event.repository.EventAreaRepository;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.repository.EventSeatRepository;
import com.smartevent.modules.event.repository.VenueRepository;
import com.smartevent.modules.event.service.EventAreaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventAreaServiceImpl implements EventAreaService {

    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final EventAreaRepository eventAreaRepository;
    private final EventSeatRepository eventSeatRepository;

    @Override
    @Transactional
    public EventAreaResponse createArea(UUID eventId, UUID currentUserId, boolean isAdmin, EventAreaRequest request) {
        /*Tìm event theo eventId và kiểm tra quyền sở hữu*/
        Event event = getEventAndVerifyAccess(eventId, currentUserId, isAdmin);

        // Kiểm tra trạng thái
        validateEventStateForModification(event);

        //Kiểm tra có trùng sự kiện hay tên chưa
        if (eventAreaRepository.existsByEventIdAndName(eventId, request.name())) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, "Khu vực '" + request.name() + "' đã tồn tại trong sự kiện");
        }

        // Kiểm tra số lượng ghế ngồi tại địa điểm đó
        validateVenueCapacity(event, request.capacity(), null);

        EventArea area = new EventArea(
                eventId,
                request.name(),
                request.areaType(),
                request.capacity(),
                request.sortOrder() != null ? request.sortOrder() : 0,
                request.description()
        );

        EventArea savedArea = eventAreaRepository.save(area);
        log.info("Tạo khu vực vé mới: {} cho sự kiện {}", savedArea.getName(), eventId);

        return EventAreaResponse.of(savedArea, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventAreaResponse> getAreasByEventId(UUID eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new EventException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sự kiện");
        }

        // Tạo 1 list khu vực/khán đài
        List<EventArea> areas = eventAreaRepository.findByEventIdOrderBySortOrderAsc(eventId);

        return areas.stream()
                .map(area -> {
                    long configuredSeats = eventSeatRepository.countByEventAreaId(area.getId());
                    return EventAreaResponse.of(area, configuredSeats);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EventAreaResponse getAreaById(UUID areaId) {
        EventArea area = eventAreaRepository.findById(areaId)
                .orElseThrow(() -> new EventException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy khu vực"));

        long configuredSeats = eventSeatRepository.countByEventAreaId(area.getId());
        return EventAreaResponse.of(area, configuredSeats);
    }

    @Override
    @Transactional
    public EventAreaResponse updateArea(UUID areaId, UUID currentUserId, boolean isAdmin, EventAreaRequest request) {
        EventArea area = eventAreaRepository.findById(areaId)
                .orElseThrow(() -> new EventException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy khu vực"));

        Event event = getEventAndVerifyAccess(area.getEventId(), currentUserId, isAdmin);
        validateEventStateForModification(event);

        if (!area.getName().equals(request.name()) && eventAreaRepository.existsByEventIdAndName(area.getEventId(), request.name())) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, "Khu vực '" + request.name() + "' đã tồn tại trong sự kiện");
        }

        validateVenueCapacity(event, request.capacity(), areaId);

        // Nếu khu vực đang có ghế và sức chứa mới nhỏ hơn số ghế hiện có
        long currentSeats = eventSeatRepository.countByEventAreaId(areaId);
        if (request.capacity() < currentSeats) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Sức chứa mới (" + request.capacity() + ") không được nhỏ hơn số ghế đã tạo (" + currentSeats + ")");
        }

        area.setName(request.name());
        area.setAreaType(request.areaType());
        area.setCapacity(request.capacity());
        area.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        area.setDescription(request.description());

        EventArea updatedArea = eventAreaRepository.save(area);
        log.info("Cập nhật khu vực vé: {} (ID: {})", updatedArea.getName(), areaId);

        return EventAreaResponse.of(updatedArea, currentSeats);
    }

    @Override
    @Transactional
    public void deleteArea(UUID areaId, UUID currentUserId, boolean isAdmin) {
        EventArea area = eventAreaRepository.findById(areaId)
                .orElseThrow(() -> new EventException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy khu vực"));

        Event event = getEventAndVerifyAccess(area.getEventId(), currentUserId, isAdmin);
        validateEventStateForModification(event);

        eventAreaRepository.delete(area);
        log.warn("Đã xóa khu vực vé: {} (ID: {})", area.getName(), areaId);
    }

    private Event getEventAndVerifyAccess(UUID eventId, UUID currentUserId, boolean isAdmin) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sự kiện"));

        if (!isAdmin && !event.getOrganizerId().equals(currentUserId)) {
            throw new EventException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền quản lý khu vực của sự kiện này");
        }

        return event;
    }

    private void validateEventStateForModification(Event event) {
        if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.PENDING_APPROVAL) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Chỉ có thể chỉnh sửa khu vực khi sự kiện ở trạng thái Nháp hoặc Chờ duyệt");
        }
    }

    private void validateVenueCapacity(Event event, int newAreaCapacity, UUID excludeAreaId) {
        if (event.getVenueId() != null) {
            Venue venue = venueRepository.findById(event.getVenueId()).orElse(null);
            if (venue != null && venue.getCapacity() != null) {
                int otherCapacity = eventAreaRepository.sumCapacityByEventIdExcluding(event.getId(), excludeAreaId);
                int totalCapacity = otherCapacity + newAreaCapacity;

                if (totalCapacity > venue.getCapacity()) {
                    throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "Tổng sức chứa các khu vực (" + totalCapacity + ") vượt quá sức chứa tối đa của địa điểm (" + venue.getCapacity() + ")");
                }
            }
        }
    }
}

