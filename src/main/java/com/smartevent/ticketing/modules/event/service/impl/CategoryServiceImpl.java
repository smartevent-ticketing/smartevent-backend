package com.smartevent.ticketing.modules.event.service.impl;

import com.smartevent.ticketing.modules.event.dto.request.CategoryRequest;
import com.smartevent.ticketing.modules.event.dto.response.CategoryResponse;
import com.smartevent.ticketing.modules.event.repository.CategoryRepository;
import com.smartevent.ticketing.modules.event.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllActiveCategories() {
        return null;
    }

    @Override
    @Transactional
    public CategoryResponse getCategoryBySlug(String slug) {
        return null;
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        return null;
    }

    @Override
    @Transactional
    public void deleteCategory(UUID id) {

    }
}
