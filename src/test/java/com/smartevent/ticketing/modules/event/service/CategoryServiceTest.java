package com.smartevent.ticketing.modules.event.service;

import com.smartevent.ticketing.common.error.ErrorCode;
import com.smartevent.ticketing.modules.event.dto.request.CategoryRequest;
import com.smartevent.ticketing.modules.event.dto.response.CategoryResponse;
import com.smartevent.ticketing.modules.event.entity.Category;
import com.smartevent.ticketing.modules.event.exception.EventException;
import com.smartevent.ticketing.modules.event.repository.CategoryRepository;
import com.smartevent.ticketing.modules.event.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @Test
    @DisplayName("Tạo danh mục thành công - Tự động sinh slug và lưu DB")
    void createCategory_Success() {
        CategoryRequest request = new CategoryRequest("Âm Nhạc & Concert", "Mô tả âm nhạc");

        when(categoryRepository.existsBySlug("am-nhac-concert")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category c = invocation.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        CategoryResponse response = categoryService.createCategory(request);

        assertNotNull(response);
        assertEquals("Âm Nhạc & Concert", response.name());
        assertEquals("am-nhac-concert", response.slug());
        assertEquals("ACTIVE", response.status());

        verify(categoryRepository, times(1)).save(any(Category.class));
    }

    @Test
    @DisplayName("Tạo danh mục trùng slug - Ném lỗi BUSINESS_RULE_VIOLATION")
    void createCategory_DuplicateSlug_ThrowsBusinessRuleViolation() {
        CategoryRequest request = new CategoryRequest("Âm Nhạc", "Mô tả");

        when(categoryRepository.existsBySlug("am-nhac")).thenReturn(true);

        EventException exception = assertThrows(EventException.class, () ->
                categoryService.createCategory(request)
        );

        assertEquals(ErrorCode.BUSINESS_RULE_VIOLATION, exception.getErrorCode());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lấy tất cả danh mục ACTIVE - Trả về danh sách")
    void getAllActiveCategories_Success() {
        Category c1 = new Category("Âm Nhạc", "am-nhac", "Mô tả");
        c1.setId(UUID.randomUUID());
        Category c2 = new Category("Thể Thao", "the-thao", "Mô tả");
        c2.setId(UUID.randomUUID());

        when(categoryRepository.findByStatus("ACTIVE")).thenReturn(List.of(c1, c2));

        List<CategoryResponse> responses = categoryService.getAllActiveCategories();

        assertEquals(2, responses.size());
        assertEquals("am-nhac", responses.get(0).slug());
        assertEquals("the-thao", responses.get(1).slug());
    }

    @Test
    @DisplayName("Tìm danh mục theo slug tồn tại - Trả về CategoryResponse")
    void getCategoryBySlug_Success() {
        Category category = new Category("Âm Nhạc", "am-nhac", "Mô tả");
        category.setId(UUID.randomUUID());

        when(categoryRepository.findBySlug("am-nhac")).thenReturn(Optional.of(category));

        CategoryResponse response = categoryService.getCategoryBySlug("am-nhac");

        assertNotNull(response);
        assertEquals("am-nhac", response.slug());
    }

    @Test
    @DisplayName("Tìm danh mục theo slug không tồn tại - Ném lỗi RESOURCE_NOT_FOUND")
    void getCategoryBySlug_NotFound_ThrowsResourceNotFound() {
        when(categoryRepository.findBySlug("khong-ton-tai")).thenReturn(Optional.empty());

        EventException exception = assertThrows(EventException.class, () ->
                categoryService.getCategoryBySlug("khong-ton-tai")
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("Cập nhật danh mục thành công - Sinh lại slug mới")
    void updateCategory_Success() {
        UUID id = UUID.randomUUID();
        Category existingCategory = new Category("Âm Nhạc", "am-nhac", "Mô tả cũ");
        existingCategory.setId(id);

        CategoryRequest updateRequest = new CategoryRequest("Âm Nhạc Mới", "Mô tả mới");

        when(categoryRepository.findById(id)).thenReturn(Optional.of(existingCategory));
        when(categoryRepository.existsBySlug("am-nhac-moi")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.updateCategory(id, updateRequest);

        assertNotNull(response);
        assertEquals("Âm Nhạc Mới", response.name());
        assertEquals("am-nhac-moi", response.slug());
        assertEquals("Mô tả mới", response.description());
    }

    @Test
    @DisplayName("Cập nhật danh mục trùng slug với danh mục khác - Ném lỗi BUSINESS_RULE_VIOLATION")
    void updateCategory_DuplicateSlug_ThrowsBusinessRuleViolation() {
        UUID id = UUID.randomUUID();
        Category existingCategory = new Category("Âm Nhạc", "am-nhac", "Mô tả");
        existingCategory.setId(id);

        CategoryRequest updateRequest = new CategoryRequest("Thể Thao", "Mô tả");

        when(categoryRepository.findById(id)).thenReturn(Optional.of(existingCategory));
        when(categoryRepository.existsBySlug("the-thao")).thenReturn(true);

        EventException exception = assertThrows(EventException.class, () ->
                categoryService.updateCategory(id, updateRequest)
        );

        assertEquals(ErrorCode.BUSINESS_RULE_VIOLATION, exception.getErrorCode());
    }

    @Test
    @DisplayName("Xóa danh mục (Soft Delete) - Đổi status thành INACTIVE và lưu DB")
    void deleteCategory_Success() {
        UUID id = UUID.randomUUID();
        Category category = new Category("Âm Nhạc", "am-nhac", "Mô tả");
        category.setId(id);
        category.setStatus("ACTIVE");

        when(categoryRepository.findById(id)).thenReturn(Optional.of(category));

        categoryService.deleteCategory(id);

        assertEquals("INACTIVE", category.getStatus());
        verify(categoryRepository, times(1)).save(category);
        verify(categoryRepository, never()).delete(any());
    }
}
