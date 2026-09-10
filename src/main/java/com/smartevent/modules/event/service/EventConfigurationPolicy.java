package com.smartevent.modules.event.service;

import com.smartevent.common.enums.AreaType;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventArea;
import java.util.UUID;

/** Cross-module constraints are implemented by the application layer. */
public interface EventConfigurationPolicy {
    void validateAreaChange(EventArea area, int capacity, AreaType areaType);
    void validateVenueChange(UUID eventId, UUID venueId);
    void validatePublication(Event event);
}
