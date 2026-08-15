package com.smartevent.ticketing.common.pagination;


import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageRequestUtils {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    public static final String DEFAULT_SORT_BY = "createdAt";

    private PageRequestUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    // Method 1: Chỉ có page và size
    public static Pageable of(Integer page, Integer size) {
        int validPage = validatePage(page);
        int validSize = validateSize(size);
        return PageRequest.of(validPage, validSize);
    }

    // Method 2: Có thêm đối tượng Sort của Spring
    public static Pageable of(Integer page, Integer size, Sort sort) {
        int validPage = validatePage(page);
        int validSize = validateSize(size);

        // Nếu sort == null hoặc sort.isUnsorted() thì dùng PageRequest.of(validPage, validSize)
        // Ngược lại dùng PageRequest.of(validPage, validSize, sort)

        if (sort == null || sort.isUnsorted()) {
            return PageRequest.of(validPage, validSize);
        }

        return PageRequest.of(validPage, validSize, sort);
    }
    // Method 3: Nhận sortBy và sortDirection từ RequestParam của Controller
    public static Pageable of(Integer page, Integer size, String sortBy, String sortDirection) {

        // Bước 1: Chuẩn hóa tên cột (nếu người dùng không truyền thì lấy mặc định "createdAt")
        String validSortBy = (sortBy == null || sortBy.isBlank()) ? DEFAULT_SORT_BY : sortBy.trim();
        // Bước 2: Xác định chiều sắp xếp (nếu truyền "asc" thì là ASC, còn lại mặc định là DESC)
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        // Bước 3: Tạo đối tượng Sort từ chiều và tên cột đã xác định
        Sort sort = Sort.by(direction, validSortBy);
        // Bước 4: Gọi hàm of(page, size, sort) đã viết trước đó để trả về Pageable
        return of(page, size, sort);
    }

    private static int validatePage(Integer page) {
        if (page == null || page < 0) {
            return DEFAULT_PAGE; // 0
        }
        return page;
    }
    private static int validateSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE; // 20
        }
        if (size > MAX_SIZE) {
            return MAX_SIZE; // Ép về tối đa 100
        }
        return size;
    }
}
