package com.smartevent.modules.event.service.impl;

import com.smartevent.common.enums.AreaType;
import com.smartevent.common.enums.SalePhaseStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.dto.request.CreateEventSetupRequest;
import com.smartevent.modules.event.dto.request.EventAreaRequest;
import com.smartevent.modules.event.dto.request.GenerateSeatsRequest;
import com.smartevent.modules.event.dto.response.EventResponse;
import com.smartevent.modules.event.exception.EventException;
import com.smartevent.modules.event.service.EventAreaService;
import com.smartevent.modules.event.service.EventSeatService;
import com.smartevent.modules.event.service.EventService;
import com.smartevent.modules.event.service.EventSetupService;
import com.smartevent.modules.ticketing.dto.request.TicketSalePhaseRequest;
import com.smartevent.modules.ticketing.dto.request.TicketTypeRequest;
import com.smartevent.modules.ticketing.service.TicketSalePhaseService;
import com.smartevent.modules.ticketing.service.TicketTypeService;
import java.time.Instant;
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

    @Override
    @Transactional
    public EventResponse createAndSubmit(UUID organizerId, CreateEventSetupRequest request) {
        if (request.event().venueId() == null || request.event().categoryIds() == null || request.event().categoryIds().isEmpty()) {
            throw new EventException(ErrorCode.VALIDATION_ERROR, "Vui lòng chọn địa điểm và danh mục trước khi gửi duyệt");
        }
        UUID eventId = eventService.createEvent(organizerId, request.event()).id();
        int sortOrder = 0;
        for (CreateEventSetupRequest.Tier tier : request.tiers()) {
            UUID areaId = eventAreaService.createArea(eventId, organizerId, false,
                    new EventAreaRequest(tier.name().trim(), tier.areaType(), tier.capacity(), sortOrder++, null)).id();
            if (tier.areaType() == AreaType.SEATED) generateSeats(areaId, organizerId, tier.capacity());
            UUID typeId = ticketTypeService.createTicketType(eventId, organizerId, false,
                    new TicketTypeRequest(areaId, tier.name().trim(), null, "ACTIVE")).id();
            ticketSalePhaseService.createSalePhase(typeId, organizerId, false,
                    new TicketSalePhaseRequest("Mở bán chính thức", tier.price(), tier.capacity(),
                            Instant.now(), request.event().endTime(), 4, null, SalePhaseStatus.ACTIVE));
        }
        return eventService.submitForApproval(eventId, organizerId, false);
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
