package com.smartevent.ticketing.common.util;

import com.github.slugify.Slugify;

public final class SlugUtils {

    private static final Slugify SLUGIFY = Slugify.builder()
            .transliterator(true) // Xử lý chuyển đổi tiếng Việt có dấu
            .build();

    private SlugUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static String toSlug(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return SLUGIFY.slugify(input.trim());
    }
}
