package com.smartevent.ticketing.modules.event.repository;

import com.smartevent.ticketing.modules.event.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VenueRepository extends JpaRepository<Venue, UUID> {

    // Lấy danh sách các địa điểm đang hoạt động
    List<Venue> findByStatus(String status);

    // Lấy địa điểm theo thành phố, ví dụ: "Hà Nội").
    List<Venue> findByCityIgnoreCaseAndStatus(String city, String status);

    // Kiểm tra xem địa điểm này đã từng được tạo ở thành phố đó chưa để tránh tạo trùng).
    boolean existsByNameAndCity(String name, String city);
}
