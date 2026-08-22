package com.smartevent.modules.storage.service.impl;

import com.smartevent.common.enums.FileScanStatus;
import com.smartevent.common.enums.FileVisibility;
import com.smartevent.common.error.BusinessException;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.storage.dto.response.FileUploadResponse;
import com.smartevent.modules.storage.dto.response.PresignedUrlResponse;
import com.smartevent.modules.storage.entity.FileEntity;
import com.smartevent.modules.storage.exception.FileException;
import com.smartevent.modules.storage.repository.FileRepository;
import com.smartevent.modules.storage.service.StorageService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements StorageService {

    private final MinioClient minioClient;
    private final FileRepository fileRepository;

    @Value("${app.storage.minio.bucket}")
    private String defaultBucket;
    @Value("${app.storage.minio.endpoint}")
    private String endpoint;

    @Override
    @Transactional
    public FileUploadResponse uploadFile(MultipartFile file, UUID ownerId, String folder, FileVisibility visibility) {

        if (file == null || file.isEmpty()) {
            throw new FileException(
                    ErrorCode.VALIDATION_ERROR,
                    "File rỗng không được để trống"
            );
        }

        // Kiểm tra dung lượng tối đa 10MB
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new FileException(ErrorCode.VALIDATION_ERROR, "Kích thước tệp vượt quá giới hạn cho phép (Tối đa 10MB)");
        }

        // Kiểm tra MIME-Type thực tế
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/") && !contentType.equals("application/pdf"))) {
            throw new FileException(ErrorCode.VALIDATION_ERROR, "Loại nội dung (MIME-Type) không được phép. Chỉ chấp nhận ảnh hoặc PDF");
        }
        if ("image/svg+xml".equalsIgnoreCase(contentType)) {
            throw new FileException(ErrorCode.VALIDATION_ERROR, "Định dạng SVG không được hỗ trợ vì lý do an ninh");
        }

        // 2. Sinh đường dẫn độc nhất trên MinIO (objectName) sau khi sanitize folder
        String sanitized = (folder != null) ? folder.replaceAll("[^a-zA-Z0-9_-]", "") : "";
        String sanitizedFolder = sanitized.isBlank() ? "general" : sanitized;
        String extension = extractExtension(file.getOriginalFilename());
        java.util.List<String> allowedExtensions = java.util.List.of(".jpg", ".jpeg", ".png", ".webp", ".pdf");
        if (!allowedExtensions.contains(extension.toLowerCase())) {
            throw new FileException(ErrorCode.VALIDATION_ERROR, "Định dạng tệp không hợp lệ. Chỉ chấp nhận JPG, PNG, WEBP, PDF");
        }

        String objectName = sanitizedFolder + "/" + UUID.randomUUID() + extension;

        // 3. Đẩy file lên MinIO
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(defaultBucket)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
        } catch (Exception e) {
            log.error("Lỗi khi upload file lên MinIO: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể tải file lên hệ thống lưu trữ");
        }

        // 4. Lưu metadata vào Database (PostgreSQL)
        FileEntity fileEntity = new FileEntity(
                ownerId,
                defaultBucket,
                objectName,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                null, // checksum (để null hoặc bổ sung sau)
                visibility
        );

        fileEntity.setScanStatus(FileScanStatus.CLEAN);
        FileEntity savedEntity = fileRepository.save(fileEntity);

        // 5. Xác định URL trả về
        String fileUrl;
        if (visibility == FileVisibility.PUBLIC) {
            // File công khai: ghép endpoint + bucket + objectName
            fileUrl = endpoint + "/" + defaultBucket + "/" + objectName;
        } else {
            // File riêng tư: sinh Presigned URL tạm thời
            fileUrl = generatePresignedUrl(savedEntity.getBucketName(), savedEntity.getObjectName(), 15);
        }
        return FileUploadResponse.of(savedEntity, fileUrl);
    }

    @Override
    @Transactional
    public PresignedUrlResponse getPresignedUrl(UUID fileId, UUID currentUserId) {

        FileEntity fileEntity = fileRepository.findById(fileId)
                .orElseThrow(() -> new FileException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "File không tồn tại!"
                ));

        if (fileEntity.getVisibility() == FileVisibility.PRIVATE) {
            if (fileEntity.getOwnerId() == null || !fileEntity.getOwnerId().equals(currentUserId)) {
                throw new FileException(
                        ErrorCode.ACCESS_DENIED,
                        "Bạn không có quyền truy cập file này"
                );
            }
        }

        int expiryMinutes = 15;
        String url = generatePresignedUrl(fileEntity.getBucketName(), fileEntity.getObjectName(), expiryMinutes);
        Instant expiresAt = Instant.now().plus(expiryMinutes, ChronoUnit.MINUTES);

        return new PresignedUrlResponse(fileId, url, expiresAt);
    }

    @Override
    @Transactional
    public void deleteFile(UUID fileId, UUID currentUserId) {
        FileEntity fileEntity = fileRepository.findById(fileId)
                .orElseThrow(() -> new FileException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy file"));

        // Kiểm tra quyền: chỉ người sở hữu mới được xóa file
        if (fileEntity.getOwnerId() == null || !fileEntity.getOwnerId().equals(currentUserId)) {
            throw new FileException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền xóa file này");
        }

        // 1. Xóa file vật lý trên MinIO
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(fileEntity.getBucketName())
                            .object(fileEntity.getObjectName())
                            .build()
            );
        } catch (Exception e) {
            log.error("Lỗi khi xóa file trên MinIO: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể xóa file trên hệ thống lưu trữ");
        }

        // 2. Xóa metadata trong PostgreSQL
        fileRepository.delete(fileEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public FileEntity getFileEntity(UUID fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new FileException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin file"));
    }



    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf("."));
    }

    private String generatePresignedUrl(String bucket, String objectName, int minutes) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(minutes, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            log.error("Lỗi khi sinh Presigned URL từ MinIO: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể tạo link tải file tạm thời");
        }
    }
}

