package com.smartevent.modules.event.service;

import com.smartevent.modules.event.dto.request.CategoryRequest;
import com.smartevent.modules.event.dto.response.CategoryResponse;

import java.util.List;
import java.util.UUID;

public interface CategoryService {

    CategoryResponse createCategory(CategoryRequest request);

    List<CategoryResponse> getAllActiveCategories();

    CategoryResponse getCategoryBySlug(String slug);

    CategoryResponse updateCategory(UUID id, CategoryRequest request);

    void deleteCategory(UUID id);
}

