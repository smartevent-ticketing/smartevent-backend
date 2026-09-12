package com.smartevent.modules.event.service;

import com.smartevent.common.enums.EventFileType;
import com.smartevent.modules.event.dto.request.UpdateMediaOrderRequest;
import com.smartevent.modules.event.dto.response.EventMediaResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface EventMediaService {

    EventMediaResponse uploadMedia(UUID eventId, MultipartFile file, EventFileType fileType, UUID currentUserId, boolean isAdmin);

    List<EventMediaResponse> getEventMedia(UUID eventId, UUID currentUserId, boolean isAdmin);

    void updateMediaOrder(UUID eventId, UpdateMediaOrderRequest request, UUID currentUserId, boolean isAdmin);

    void deleteMedia(UUID eventId, UUID eventFileId, UUID currentUserId, boolean isAdmin);
}
