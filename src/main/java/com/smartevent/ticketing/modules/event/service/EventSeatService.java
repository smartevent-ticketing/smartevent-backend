package com.smartevent.ticketing.modules.event.service;

import com.smartevent.ticketing.common.api.PageResponse;
import com.smartevent.ticketing.modules.event.dto.request.EventSeatRequest;
import com.smartevent.ticketing.modules.event.dto.request.GenerateSeatsRequest;
import com.smartevent.ticketing.modules.event.dto.response.EventSeatResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface EventSeatService {

    List<EventSeatResponse> generateSeats(UUID areaId, UUID currentUserId, boolean isAdmin, GenerateSeatsRequest request);

    PageResponse<EventSeatResponse> getSeatsByArea(UUID areaId, Pageable pageable);

    List<EventSeatResponse> getAvailableSeatsByArea(UUID areaId);

    EventSeatResponse createSingleSeat(UUID areaId, UUID currentUserId, boolean isAdmin, EventSeatRequest request);

    void deleteSeat(UUID seatId, UUID currentUserId, boolean isAdmin);

    void deleteAllSeatsInArea(UUID areaId, UUID currentUserId, boolean isAdmin);
}