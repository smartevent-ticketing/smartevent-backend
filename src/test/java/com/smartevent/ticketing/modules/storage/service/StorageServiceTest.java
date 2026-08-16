package com.smartevent.ticketing.modules.storage.service;

import com.smartevent.ticketing.common.enums.FileVisibility;
import com.smartevent.ticketing.common.error.ErrorCode;
import com.smartevent.ticketing.modules.storage.dto.response.FileUploadResponse;
import com.smartevent.ticketing.modules.storage.dto.response.PresignedUrlResponse;
import com.smartevent.ticketing.modules.storage.entity.FileEntity;
import com.smartevent.ticketing.modules.storage.exception.FileException;
import com.smartevent.ticketing.modules.storage.repository.FileRepository;
import com.smartevent.ticketing.modules.storage.service.impl.StorageServiceImpl;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private MinioClient minioClient; // Mock giả lập MinIO

    @Mock
    private FileRepository fileRepository; // Mock giả lập Database

    @InjectMocks
    private StorageServiceImpl storageService; // Service THẬT cần test

    @BeforeEach
    void setUp() {
        // Vì @Value("${app.storage.minio...}") không tự inject trong Unit Test,
        // ta dùng ReflectionTestUtils để gán giá trị giả cho 2 biến này:
        ReflectionTestUtils.setField(storageService, "defaultBucket", "smart-event");
        ReflectionTestUtils.setField(storageService, "endpoint", "http://localhost:9000");
    }

    @Test
    @DisplayName("Upload file thành công (PUBLIC) - Trả về FileUploadResponse và lưu DB")
    void uploadFile_Success_Public() throws Exception {
        // 1. ARRANGE (Chuẩn bị)
        UUID ownerId = UUID.randomUUID();
        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "avatar.png", "image/png", "fake image content".getBytes()
        );

        FileEntity mockSavedEntity = new FileEntity(
                ownerId, "smart-event", "avatars/abc-123.png", "avatar.png",
                "image/png", 17L, null, FileVisibility.PUBLIC
        );
        mockSavedEntity.setId(UUID.randomUUID());

        // Dạy cho fileRepository giả: Khi save file bất kỳ -> trả về mockSavedEntity
        when(fileRepository.save(any(FileEntity.class))).thenReturn(mockSavedEntity);

        // 2. ACT (Thực thi)
        FileUploadResponse response = storageService.uploadFile(mockFile, ownerId, "avatars", FileVisibility.PUBLIC);

        // 3. ASSERT (Kiểm tra)
        assertNotNull(response);
        assertEquals("avatar.png", response.originName());

        assertTrue(response.url().startsWith("http://localhost:9000/smart-event/avatars/"));
        assertTrue(response.url().endsWith(".png"));

        // Xác nhận fileRepository.save ĐÃ ĐƯỢC GỌI đúng 1 lần
        verify(fileRepository, times(1)).save(any(FileEntity.class));
    }

    @Test
    @DisplayName("Upload file rỗng - Ném lỗi VALIDATION_ERROR")
    void uploadFile_EmptyFile_ThrowsException() {
        // 1. ARRANGE
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        // 2. ACT & ASSERT (Dùng assertThrows để kiểm tra xem có ném Exception không)
        FileException exception = assertThrows(FileException.class, () -> {
            storageService.uploadFile(emptyFile, UUID.randomUUID(), "avatars", FileVisibility.PUBLIC);
        });

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());

        // Xác nhận fileRepository CHƯA TỪNG bị gọi lưu
        verify(fileRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lấy Presigned URL file PRIVATE của người khác - Ném lỗi ACCESS_DENIED")
    void getPresignedUrl_OtherUserPrivateFile_ThrowsAccessDenied() {
        // 1. ARRANGE
        UUID fileId = UUID.randomUUID();
        UUID ownerA = UUID.randomUUID();
        UUID userB_Hacker = UUID.randomUUID();

        FileEntity privateFileOfA = new FileEntity(
                ownerA, "smart-event", "tickets/ticket.pdf", "ticket.pdf",
                "application/pdf", 1000L, null, FileVisibility.PRIVATE
        );

        when(fileRepository.findById(fileId)).thenReturn(Optional.of(privateFileOfA));

        // 2. ACT & ASSERT
        FileException exception = assertThrows(FileException.class, () -> {
            storageService.getPresignedUrl(fileId, userB_Hacker); // User B cố tình xem trộm file của User A
        });

        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
    }

    @Test
    @DisplayName("Xóa file chính chủ - Gọi xóa MinIO và xóa trong Database")
    void deleteFile_Success() throws Exception {
        // 1. ARRANGE
        UUID fileId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        FileEntity file = new FileEntity(
                ownerId, "smart-event", "avatars/my-pic.png", "my-pic.png",
                "image/png", 500L, null, FileVisibility.PUBLIC
        );

        when(fileRepository.findById(fileId)).thenReturn(Optional.of(file));

        // 2. ACT
        storageService.deleteFile(fileId, ownerId);

        // 3. ASSERT: Kiểm tra xem minioClient và repository đã thực sự nhận lệnh xóa chưa
        verify(minioClient, times(1)).removeObject(any(RemoveObjectArgs.class));
        verify(fileRepository, times(1)).delete(file);
    }
}