package com.smartevent.modules.ticketing.service.impl;

import com.smartevent.common.enums.EventStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventArea;
import com.smartevent.modules.event.repository.EventAreaRepository;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.ticketing.dto.request.TicketTypeRequest;
import com.smartevent.modules.ticketing.dto.response.TicketTypeResponse;
import com.smartevent.modules.ticketing.entity.TicketType;
import com.smartevent.modules.ticketing.exception.TicketingException;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import com.smartevent.modules.ticketing.service.TicketTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketTypeServiceImpl implements TicketTypeService {

    private final EventRepository eventRepository;
    private final EventAreaRepository eventAreaRepository;
    private final TicketTypeRepository ticketTypeRepository;

    @Override
    @Transactional
    public TicketTypeResponse createTicketType(UUID eventId, UUID currentUserId, boolean isAdmin, TicketTypeRequest request) {
        // 1. Xác thực sự kiện và quyền sở hữu (Chỉ Owner hoặc Admin)
        Event event = getEventAndVerifyAccess(eventId, currentUserId, isAdmin);

        // 2. State Guard (Chỉ cho tạo khi DRAFT / PENDING)
        validateEventStateForModification(event);

        // 3. Kiểm tra Khán đài có tồn tại và thuộc đúng sự kiện này không
        EventArea area = eventAreaRepository.findById(request.eventAreaId())
                .orElseThrow(() -> new TicketingException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy khu vực/khán đài"));

        if (!area.getEventId().equals(eventId)) {
            throw new TicketingException(ErrorCode.BUSINESS_RULE_VIOLATION, "Khu vực không thuộc sự kiện này");
        }

        // 4. Kiểm tra trùng tên loại vé trong cùng 1 sự kiện
        if (ticketTypeRepository.existsByEventIdAndName(eventId, request.name())) {
            throw new TicketingException(ErrorCode.TICKET_TYPE_NAME_EXISTS, "Tên loại vé '" + request.name() + "' đã tồn tại trong sự kiện");
        }

        // 5. Tạo Entity và Lưu Database
        TicketType ticketType = new TicketType(
                eventId,
                request.eventAreaId(),
                request.name(),
                request.description(),
                request.status()
        );

        TicketType saved = ticketTypeRepository.save(ticketType);
        log.info("Tạo loại vé mới: {} (ID: {}) cho sự kiện {}", saved.getName(), saved.getId(), eventId);

        // Trả về Response được làm giàu thêm thông tin Khán đài (areaName, areaType)
        return TicketTypeResponse.of(saved, area.getName(), area.getAreaType());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketTypeResponse> getTicketTypesByEventId(UUID eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new TicketingException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sự kiện");
        }

        List<TicketType> ticketTypes = ticketTypeRepository.findByEventId(eventId);
        return ticketTypes.stream()
                .map(tt -> {
                    EventArea area = eventAreaRepository.findById(tt.getEventAreaId()).orElse(null);
                    String areaName = area != null ? area.getName() : "Unknown";
                    var areaType = area != null ? area.getAreaType() : null;
                    return TicketTypeResponse.of(tt, areaName, areaType);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketTypeResponse> getTicketTypesByAreaId(UUID areaId) {
        EventArea area = eventAreaRepository.findById(areaId)
                .orElseThrow(() -> new TicketingException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy khu vực"));

        List<TicketType> ticketTypes = ticketTypeRepository.findByEventAreaId(areaId);
        return ticketTypes.stream()
                .map(tt -> TicketTypeResponse.of(tt, area.getName(), area.getAreaType()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TicketTypeResponse getTicketTypeById(UUID id) {
        TicketType ticketType = ticketTypeRepository.findById(id)
                .orElseThrow(() -> new TicketingException(ErrorCode.TICKET_TYPE_NOT_FOUND, "Không tìm thấy loại vé"));

        EventArea area = eventAreaRepository.findById(ticketType.getEventAreaId()).orElse(null);
        String areaName = area != null ? area.getName() : "Unknown";
        var areaType = area != null ? area.getAreaType() : null;

        return TicketTypeResponse.of(ticketType, areaName, areaType);
    }

    @Override
    @Transactional
    public TicketTypeResponse updateTicketType(UUID id, UUID currentUserId, boolean isAdmin, TicketTypeRequest request) {
        TicketType ticketType = ticketTypeRepository.findById(id)
                .orElseThrow(() -> new TicketingException(ErrorCode.TICKET_TYPE_NOT_FOUND, "Không tìm thấy loại vé"));

        Event event = getEventAndVerifyAccess(ticketType.getEventId(), currentUserId, isAdmin);
        validateEventStateForModification(event);

        // Nếu đổi tên loại vé -> kiểm tra trùng tên mới
        if (!ticketType.getName().equals(request.name())
                && ticketTypeRepository.existsByEventIdAndName(ticketType.getEventId(), request.name())) {
            throw new TicketingException(ErrorCode.TICKET_TYPE_NAME_EXISTS, "Tên loại vé '" + request.name() + "' đã tồn tại trong sự kiện");
        }

        // Nếu đổi sang khán đài khác -> kiểm tra khán đài mới có thuộc cùng sự kiện không
        EventArea area = eventAreaRepository.findById(request.eventAreaId())
                .orElseThrow(() -> new TicketingException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy khu vực mới"));
        if (!area.getEventId().equals(ticketType.getEventId())) {
            throw new TicketingException(ErrorCode.BUSINESS_RULE_VIOLATION, "Khu vực mới không thuộc sự kiện này");
        }

        ticketType.setName(request.name());
        ticketType.setEventAreaId(request.eventAreaId());
        ticketType.setDescription(request.description());
        if (request.status() != null) {
            ticketType.setStatus(request.status());
        }

        TicketType updated = ticketTypeRepository.save(ticketType);
        log.info("Cập nhật loại vé: {} (ID: {})", updated.getName(), id);

        return TicketTypeResponse.of(updated, area.getName(), area.getAreaType());
    }

    @Override
    @Transactional
    public void deleteTicketType(UUID id, UUID currentUserId, boolean isAdmin) {
        TicketType ticketType = ticketTypeRepository.findById(id)
                .orElseThrow(() -> new TicketingException(ErrorCode.TICKET_TYPE_NOT_FOUND, "Không tìm thấy loại vé"));

        Event event = getEventAndVerifyAccess(ticketType.getEventId(), currentUserId, isAdmin);
        validateEventStateForModification(event);

        ticketTypeRepository.delete(ticketType);
        log.warn("Đã xóa loại vé: {} (ID: {})", ticketType.getName(), id);
    }




    private Event getEventAndVerifyAccess(UUID eventId, UUID currentUserId, boolean isAdmin) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new TicketingException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sự kiện"));

        if (!isAdmin && !event.getOrganizerId().equals(currentUserId)) {
            throw new TicketingException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền quản lý loại vé của sự kiện này");
        }

        return event;
    }

    private void validateEventStateForModification(Event event) {
        if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.PENDING_APPROVAL) {
            throw new TicketingException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Chỉ có thể chỉnh sửa loại vé khi sự kiện ở trạng thái Nháp hoặc Chờ duyệt");
        }
    }
}
