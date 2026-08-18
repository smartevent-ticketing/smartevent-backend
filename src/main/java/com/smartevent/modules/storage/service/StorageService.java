package com.smartevent.modules.storage.service;

import com.smartevent.common.enums.FileVisibility;
import com.smartevent.modules.storage.dto.response.FileUploadResponse;
import com.smartevent.modules.storage.dto.response.PresignedUrlResponse;
import com.smartevent.modules.storage.entity.FileEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface StorageService {

    FileUploadResponse uploadFile(MultipartFile file, UUID ownerId, String folder, FileVisibility visibility);

    PresignedUrlResponse getPresignedUrl(UUID fileId, UUID currentUserId);

    void deleteFile(UUID fileId, UUID currentUserId);

    FileEntity getFileEntity(UUID fileId);
}

