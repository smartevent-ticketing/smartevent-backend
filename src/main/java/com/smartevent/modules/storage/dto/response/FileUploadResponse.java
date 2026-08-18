package com.smartevent.modules.storage.dto.response;

import com.smartevent.common.enums.FileVisibility;
import com.smartevent.modules.storage.entity.FileEntity;

import java.time.Instant;
import java.util.UUID;

public record FileUploadResponse (
        UUID id,
        String originName,
        String contentType,
        Long fullSize,
        String url,
        FileVisibility visibility,
        Instant createdAt
) {
    public static FileUploadResponse of(FileEntity entity, String url) {
        return new FileUploadResponse(entity.getId(), entity.getOriginalName(), entity.getContentType(), entity.getFileSize(), url, entity.getVisibility(), entity.getCreatedAt());
    }
}

