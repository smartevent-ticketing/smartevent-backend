package com.smartevent.modules.event.dto.response;

import com.smartevent.modules.event.entity.Category;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse (
        UUID id,
        String name,
        String slug,
        String description,
        String status,
        Instant createdAt
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSlug(), category.getDescription(), category.getStatus(), category.getCreatedAt());
    }
}

