package com.smartevent.modules.storage.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.common.enums.FileVisibility;
import com.smartevent.common.security.CurrentUser;
import com.smartevent.infrastructure.security.UserPrincipal;
import com.smartevent.modules.storage.dto.response.FileUploadResponse;
import com.smartevent.modules.storage.dto.response.PresignedUrlResponse;
import com.smartevent.modules.storage.service.StorageService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/storage")
public class StorageController {

    private final StorageService storageService;

    public StorageController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileUploadResponse> uploadFile(
            @RequestParam("file")MultipartFile multipartFile,
            @RequestParam(value = "folder", defaultValue = "general") String folder,
            @RequestParam(value = "visibility", defaultValue = "PRIVATE") FileVisibility visibility,
            @CurrentUser UserPrincipal currentUser) {

        return ApiResponse.success(storageService.uploadFile(multipartFile, currentUser.getId(), folder, visibility));
    }

    @GetMapping("/{fileId}/presigned-url")
    public ApiResponse<PresignedUrlResponse> getPresignedUrl(
            @PathVariable UUID fileId,
            @CurrentUser UserPrincipal currentUser
    ) {
        return ApiResponse.success(storageService.getPresignedUrl(fileId, currentUser.getId()));
    }

    @DeleteMapping("/{fileId}")
    public ApiResponse<Void> deleteFile(
            @PathVariable UUID fileId,
            @CurrentUser UserPrincipal currentUser
    ) {
        storageService.deleteFile(fileId, currentUser.getId());
        return ApiResponse.ok("Xóa file thành công");
    }


}

