package com.smartevent.modules.event.repository;

import com.smartevent.modules.event.entity.EventArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventAreaRepository extends JpaRepository<EventArea, UUID> {

    /*Lấy tất cả các khu vực của sự kiện sắp xếp theo thứ tự hiển thị*/
    List<EventArea> findByEventIdOrderBySortOrderAsc(UUID eventId);

    Optional<EventArea> findByIdAndEventId(UUID id, UUID eventId);

    /*Chống tạo trùng tên khán đài trong cùng 1 sự kiện*/
    boolean existsByEventIdAndName(UUID eventId, String name);

    // Tính tổng sức chứa của các Area trong Event (loại trừ chính Area đang sửa)
    @Query("""
        SELECT COALESCE(SUM(a.capacity), 0) FROM EventArea a
        WHERE a.eventId = :eventId
          AND (:excludeAreaId IS NULL OR a.id != :excludeAreaId)
    """)
    int sumCapacityByEventIdExcluding(
            @Param("eventId") UUID eventId,
            @Param("excludeAreaId") UUID excludeAreaId
    );
}

