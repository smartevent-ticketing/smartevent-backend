package com.smartevent.ticketing.modules.event.service.impl;

import com.github.slugify.Slugify;
import com.smartevent.ticketing.common.error.ErrorCode;
import com.smartevent.ticketing.modules.event.dto.request.CategoryRequest;
import com.smartevent.ticketing.modules.event.dto.response.CategoryResponse;
import com.smartevent.ticketing.modules.event.entity.Category;
import com.smartevent.ticketing.modules.event.exception.CategoryException;
import com.smartevent.ticketing.modules.event.repository.CategoryRepository;
import com.smartevent.ticketing.modules.event.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    private final Slugify slugify = Slugify.builder().lowerCase(true).build();

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {

        String slug = slugify.slugify(request.name());

        if(categoryRepository.existsBySlug(slug)) {
            throw new CategoryException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Danh mục này đã tồn tại");
        }

        Category category = new Category(
                request.name(),
                slug,
                request.description()
        );

        Category savedCategory = categoryRepository.save(category);

        return CategoryResponse.from(savedCategory);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllActiveCategories() {

        List<Category> categories = categoryRepository.findByStatus("ACTIVE");

        return categories.stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public CategoryResponse getCategoryBySlug(String slug) {

        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new CategoryException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy danh mục"
                ));

        return CategoryResponse.from(category);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy danh mục"));

        String newSlug = slugify.slugify(request.name());

        if (!newSlug.equals(category.getSlug()) && categoryRepository.existsBySlug(newSlug)) {
            throw new CategoryException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Slug đã bị trùng"
            );
        }

        category.setName(request.name());
        category.setSlug(newSlug);
        category.setDescription(request.description());

        Category updateCategory = categoryRepository.save(category);

        return CategoryResponse.from(updateCategory);
    }

    @Override
    @Transactional
    public void deleteCategory(UUID id) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy danh mục"));

        category.setStatus("INACTIVE");

        categoryRepository.save(category);

    }
}
