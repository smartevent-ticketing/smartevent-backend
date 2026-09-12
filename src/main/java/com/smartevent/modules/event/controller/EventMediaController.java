package com.smartevent.modules.event.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.common.enums.EventFileType;
import com.smartevent.common.security.CurrentUser;
import com.smartevent.infrastructure.security.UserPrincipal;
import com.smartevent.modules.event.dto.request.UpdateMediaOrderRequest;
import com.smartevent.modules.event.dto.response.EventMediaResponse;
import com.smartevent.modules.event.service.EventMediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}/media")
@RequiredArgsConstructor
@Tag(name = "Event Media Management", description = "APIs quản lý ảnh Banner và Gallery của sự kiện")
public class EventMediaController {

    private final EventMediaService eventMediaService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Tải lên ảnh Banner hoặc Gallery cho sự kiện (Yêu cầu ORGANIZER hoặc ADMIN)")
    public ResponseEntity<ApiResponse<EventMediaResponse>> uploadMedia(
            @PathVariable UUID eventId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") EventFileType type,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        EventMediaResponse response = eventMediaService.uploadMedia(eventId, file, type, currentUser.getId(), isAdmin);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách media của sự kiện (Công khai nếu sự kiện PUBLISHED, yêu cầu quyền nếu DRAFT)")
    public ResponseEntity<ApiResponse<List<EventMediaResponse>>> getEventMedia(
            @PathVariable UUID eventId,
            @CurrentUser UserPrincipal currentUser
    ) {
        UUID currentUserId = currentUser != null ? currentUser.getId() : null;
        boolean isAdmin = checkIsAdmin(currentUser);
        List<EventMediaResponse> response = eventMediaService.getEventMedia(eventId, currentUserId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/order")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Cập nhật thứ tự hiển thị của các ảnh trong bộ sưu tập (Yêu cầu ORGANIZER hoặc ADMIN)")
    public ResponseEntity<ApiResponse<Void>> updateMediaOrder(
            @PathVariable UUID eventId,
            @Valid @RequestBody UpdateMediaOrderRequest request,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        eventMediaService.updateMediaOrder(eventId, request, currentUser.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật thứ tự ảnh thành công"));
    }

    @DeleteMapping("/{eventFileId}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Gỡ bỏ ảnh khỏi sự kiện và xóa tệp tin trên MinIO (Yêu cầu ORGANIZER hoặc ADMIN)")
    public ResponseEntity<ApiResponse<Void>> deleteMedia(
            @PathVariable UUID eventId,
            @PathVariable UUID eventFileId,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        eventMediaService.deleteMedia(eventId, eventFileId, currentUser.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.ok("Xóa ảnh thành công"));
    }

    private boolean checkIsAdmin(UserPrincipal currentUser) {
        return currentUser != null && currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
