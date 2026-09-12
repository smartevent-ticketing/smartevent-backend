package com.smartevent.modules.event.repository;

import com.smartevent.common.enums.EventFileType;
import com.smartevent.modules.event.entity.EventFile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventFileRepository extends JpaRepository<EventFile, UUID> {

    List<EventFile> findByEventIdOrderBySortOrderAsc(UUID eventId);

    List<EventFile> findByEventIdAndFileType(UUID eventId, EventFileType fileType);

    List<EventFile> findByEventIdInOrderBySortOrderAsc(List<UUID> eventIds);

    void deleteByEventId(UUID eventId);

    // 👇 3 hàm tiện ích mới:
    Optional<EventFile> findByIdAndEventId(UUID id, UUID eventId);

    List<EventFile> findByEventIdAndFileTypeOrderBySortOrderAsc(UUID eventId, EventFileType fileType);

    long countByEventIdAndFileType(UUID eventId, EventFileType fileType);
}
