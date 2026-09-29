package com.smartevent.modules.event.service.impl;

import com.smartevent.common.enums.AreaType;
import com.smartevent.common.enums.EventStatus;
import com.smartevent.common.enums.SalePhaseStatus;
import com.smartevent.common.enums.TicketTypeStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.dto.request.CreateEventSetupRequest;
import com.smartevent.modules.event.dto.request.CompleteDraftSetupRequest;
import com.smartevent.modules.event.dto.request.EventAreaRequest;
import com.smartevent.modules.event.dto.request.GenerateSeatsRequest;
import com.smartevent.modules.event.dto.response.EventResponse;
import com.smartevent.modules.event.exception.EventException;
import com.smartevent.modules.event.repository.EventAreaRepository;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.service.EventAreaService;
import com.smartevent.modules.event.service.EventSeatService;
import com.smartevent.modules.event.service.EventService;
import com.smartevent.modules.event.service.EventSetupService;
import com.smartevent.modules.ticketing.dto.request.TicketSalePhaseRequest;
import com.smartevent.modules.ticketing.dto.request.TicketTypeRequest;
import com.smartevent.modules.ticketing.service.TicketSalePhaseService;
import com.smartevent.modules.ticketing.service.TicketTypeService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates the event and its ticket configuration through module services in one transaction. */
@Service
@RequiredArgsConstructor
public class EventSetupServiceImpl implements EventSetupService {
    private final EventService eventService;
    private final EventAreaService eventAreaService;
    private final EventSeatService eventSeatService;
    private final TicketTypeService ticketTypeService;
    private final TicketSalePhaseService ticketSalePhaseService;
    private final EventRepository eventRepository;
    private final EventAreaRepository eventAreaRepository;

    @Override
    @Transactional
    public EventResponse createAndSubmit(UUID organizerId, CreateEventSetupRequest request) {
        if (request.event().venueId() == null || request.event().categoryIds() == null || request.event().categoryIds().isEmpty()) {
            throw new EventException(ErrorCode.VALIDATION_ERROR, "Vui lòng chọn địa điểm và danh mục trước khi gửi duyệt");
        }
        UUID eventId = eventService.createEvent(organizerId, request.event()).id();
        configureTiers(eventId, organizerId, request.tiers(), request.event().endTime());
        return eventService.submitForApproval(eventId, organizerId, false);
    }

    @Override
    @Transactional
    public EventResponse completeDraftAndSubmit(UUID eventId, UUID organizerId, CompleteDraftSetupRequest request) {
        var event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new EventException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sự kiện"));
        if (!event.getOrganizerId().equals(organizerId)) {
            throw new EventException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền cấu hình sự kiện này");
        }
        // A completed request may be retried when the client did not receive its response.
        EventStatus status = event.getStatus();
        if (status == EventStatus.PENDING_APPROVAL) {
            return eventService.getEventById(eventId, organizerId, false);
        }
        if (status != EventStatus.DRAFT) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, "Chỉ bản nháp mới có thể gửi duyệt");
        }
        if (eventAreaRepository.countByEventId(eventId) != 0) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Bản nháp đã có khu vé. Vui lòng kiểm tra cấu hình trước khi gửi duyệt");
        }
        if (request.tiers().stream().anyMatch(tier -> tier == null)) {
            throw new EventException(ErrorCode.VALIDATION_ERROR, "Hạng vé không được để trống");
        }
        configureTiers(eventId, organizerId, request.tiers(), event.getEndTime());
        return eventService.submitForApproval(eventId, organizerId, false);
    }

    private void configureTiers(UUID eventId, UUID organizerId,
            List<CreateEventSetupRequest.Tier> tiers, Instant saleEndAt) {
        int sortOrder = 0;
        for (CreateEventSetupRequest.Tier tier : tiers) {
            UUID areaId = eventAreaService.createArea(eventId, organizerId, false,
                    new EventAreaRequest(tier.name().trim(), tier.areaType(), tier.capacity(), sortOrder++, null)).id();
            if (tier.areaType() == AreaType.SEATED) generateSeats(areaId, organizerId, tier.capacity());
            UUID typeId = ticketTypeService.createTicketType(eventId, organizerId, false,
                    new TicketTypeRequest(areaId, tier.name().trim(), null, TicketTypeStatus.ACTIVE)).id();
            ticketSalePhaseService.createSalePhase(typeId, organizerId, false,
                    new TicketSalePhaseRequest("Mở bán chính thức", tier.price(), tier.capacity(),
                            Instant.now(), saleEndAt, 4, null, SalePhaseStatus.ACTIVE));
        }
    }

    private void generateSeats(UUID areaId, UUID organizerId, int capacity) {
        int rows = Math.min(26, (capacity - 1) / 25 + 1);
        int seatsPerRow = capacity / rows;
        int extra = capacity % rows;
        if (extra > 0) {
            eventSeatService.generateSeats(areaId, organizerId, false,
                    new GenerateSeatsRequest("A", row(extra - 1), seatsPerRow + 1));
        }
        if (extra < rows) {
            eventSeatService.generateSeats(areaId, organizerId, false,
                    new GenerateSeatsRequest(row(extra), row(rows - 1), seatsPerRow));
        }
    }

    private String row(int index) { return String.valueOf((char) ('A' + index)); }
}
