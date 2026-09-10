package com.smartevent.modules.event.service;

import com.smartevent.common.enums.EventStatus;
import com.smartevent.modules.event.entity.Event;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class EventAccessPolicy {
    public boolean canManage(Event event, UUID userId, boolean isAdmin) {
        return isAdmin || event.getOrganizerId().equals(userId);
    }

    public boolean canModifyConfiguration(Event event) {
        return event.getStatus() == EventStatus.DRAFT || event.getStatus() == EventStatus.PENDING_APPROVAL;
    }
}
