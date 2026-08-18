package com.smartevent.ticketing.modules.event.service;

import com.smartevent.ticketing.common.api.PageResponse;
import com.smartevent.ticketing.modules.event.dto.request.CreateEventRequest;
import com.smartevent.ticketing.modules.event.dto.request.UpdateEventRequest;
import com.smartevent.ticketing.modules.event.dto.response.EventResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface EventService {
    EventResponse createEvent(UUID organizerId, CreateEventRequest request);

    EventResponse updateEvent(UUID eventId, UUID currentUserId, boolean isAdmin, UpdateEventRequest request);

    EventResponse getEventBySlug(String slug);

    EventResponse getEventById(UUID id);

    PageResponse<EventResponse> getPublishedEvents(Pageable pageable);

    PageResponse<EventResponse> getEventsByOrganizer(UUID organizerId, Pageable pageable);

    // 7. Ban tổ chức nộp sự kiện chờ Admin phê duyệt
    EventResponse submitForApproval(UUID eventId, UUID currentUserId, boolean isAdmin);

    // 8. Admin phê duyệt sự kiện (chuyển sang PUBLISHED)
    EventResponse approveEvent(UUID eventId);

    // 9. Admin từ chối phê duyệt sự kiện (trả về DRAFT)
    EventResponse rejectEvent(UUID eventId, String reason);

    // 10. Hủy sự kiện (bởi Ban tổ chức hoặc Admin)
    EventResponse cancelEvent(UUID eventId, UUID currentUserId, boolean isAdmin, String reason);
}
