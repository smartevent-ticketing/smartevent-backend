package com.smartevent.ticketing.modules.storage.service.impl;

import com.smartevent.ticketing.common.enums.FileVisibility;
import com.smartevent.ticketing.common.error.ErrorCode;
import com.smartevent.ticketing.modules.storage.dto.response.FileUploadResponse;
import com.smartevent.ticketing.modules.storage.repository.FileRepository;
import com.smartevent.ticketing.modules.storage.service.StorageService;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.lang.module.FindException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MinioStorageServiceImpl implements StorageService {

    private final MinioClient minioClient;
    private final FileRepository fileRepository;

    @Value("${app.storage.minio.bucket}") private String defaultBucket;
    @Value("${app.storage.minio.endpoint}") private String endpoint;

    @Override
    public FileUploadResponse uploadFile(MultipartFile file, UUID ownerId, String folder, FileVisibility visibility) {

        if(file == null || file.isEmpty()) {
            throw new FindException(
                    ErrorCode.VALIDATION_ERROR,
                    "File rỗng hoặc không tồn tại"
            );
        }
    }
}
