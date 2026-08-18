package com.smartevent.modules.storage.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PresignedUrlResponse (
        UUID fileId,
        String url,
        Instant expiresAt
) {
}

