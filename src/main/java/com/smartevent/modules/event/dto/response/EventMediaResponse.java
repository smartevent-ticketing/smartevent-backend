package com.smartevent.modules.event.dto.response;

import com.smartevent.common.enums.EventFileType;
import java.time.Instant;
import java.util.UUID;

public record EventMediaResponse(
        UUID eventFileId,
        UUID fileID,
        EventFileType fileType,
        String fileUrl,
        Integer sortOrder,
        Instant createdAt
        ) {
}
