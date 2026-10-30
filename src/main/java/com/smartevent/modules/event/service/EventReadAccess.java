package com.smartevent.modules.event.service;
import java.util.UUID;
import com.smartevent.common.enums.EventStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.infrastructure.security.UserPrincipal;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.repository.EventAreaRepository;
import com.smartevent.modules.event.exception.EventException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContextHolder;
@Component
@RequiredArgsConstructor
public class EventReadAccess {
    private final EventRepository events;
    private final EventAreaRepository areas;
    public boolean requireEvent(UUID id) {
        var event = events.findById(id).orElseThrow(this::denied);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean privileged = auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserPrincipal user
            && (user.getId().equals(event.getOrganizerId()) || auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        if (!privileged && event.getStatus() != EventStatus.PUBLISHED) throw denied();
        return privileged;
    }
    public boolean requireArea(UUID id) {
        return requireEvent(areas.findById(id).orElseThrow(this::denied).getEventId());
    }
    private EventException denied() {
        return new EventException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài nguyên công khai");
    }
}
