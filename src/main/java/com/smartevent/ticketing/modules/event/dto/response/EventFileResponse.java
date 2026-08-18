package com.smartevent.ticketing.modules.event.dto.response;

import com.smartevent.ticketing.common.enums.EventFileType;
import com.smartevent.ticketing.modules.event.entity.EventFile;

import java.util.UUID;

public record EventFileResponse (
        UUID id,
        UUID fileId,
        EventFileType fileType,
        Integer sortOrder
) {
    public static EventFileResponse from(EventFile eventFile) {
        return new EventFileResponse(
                eventFile.getId(),
                eventFile.getFileId(),
                eventFile.getFileType(),
                eventFile.getSortOrder()
        );
    }
}
