package com.smartevent.modules.event.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.common.api.PageResponse;
import com.smartevent.common.enums.EventStatus;
import com.smartevent.modules.event.dto.response.EventResponse;
import com.smartevent.modules.event.service.impl.EventQueryService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/events")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class EventAdministrationController {
    private final EventQueryService eventQueryService;

    @GetMapping
    public ApiResponse<PageResponse<EventResponse>> list(@RequestParam(required = false) EventStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(eventQueryService.getEventsForAdministration(status, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<EventResponse> detail(@PathVariable UUID id) {
        return ApiResponse.success(eventQueryService.getEventForAdministration(id));
    }
}
