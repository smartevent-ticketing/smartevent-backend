package com.smartevent.modules.event.repository;

import com.smartevent.modules.event.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    // Tìm danh mục theo slug URL, ví dụ: "am-nhac"
    Optional<Category> findBySlug(String slug);

    // Kiểm tra trùng lặp slug
    boolean existsBySlug(String slug);

    // Tìm kiếm tất cả các danh mục đang ACTIVE
    List<Category> findByStatus(String status);
}

