package com.smartevent.modules.event.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.modules.event.dto.request.CategoryRequest;
import com.smartevent.modules.event.dto.response.CategoryResponse;
import com.smartevent.modules.event.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Category Management", description = "APIs quản lý danh mục sự kiện (Âm nhạc, Thể thao, Hội thảo...)")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo danh mục sự kiện mới (Yêu cầu ADMIN)")
    public ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.success(categoryService.createCategory(request));
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả các danh mục đang hoạt động")
    public ApiResponse<List<CategoryResponse>> getAllActiveCategories() {
        return ApiResponse.success(categoryService.getAllActiveCategories());
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Lấy thông tin danh mục theo đường dẫn thân thiện (slug)")
    public ApiResponse<CategoryResponse> getCategoryBySlug(@PathVariable String slug) {
        return ApiResponse.success(categoryService.getCategoryBySlug(slug));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật thông tin danh mục theo ID (Yêu cầu ADMIN)")
    public ApiResponse<CategoryResponse> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.success(categoryService.updateCategory(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xóa mềm danh mục theo ID (Yêu cầu ADMIN)")
    public ApiResponse<Void> deleteCategory(@PathVariable UUID id) {
        categoryService.deleteCategory(id);
        return ApiResponse.ok("Xóa danh mục thành công");
    }
}

