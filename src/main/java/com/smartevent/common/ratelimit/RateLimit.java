package com.smartevent.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation giới hạn tần suất gọi API (Rate Limiting) bằng Redis In-Memory Counter.
 * Ví dụ: @RateLimit(limit = 5, durationInSeconds = 10) -> Cho phép tối đa 5 requests / 10 giây.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * Số lượng requests tối đa được phép trong khoảng thời gian durationInSeconds
     */
    int limit() default 10;

    /**
     * Khoảng thời gian cửa sổ trượt tính bằng giây (mặc định: 60s)
     */
    int durationInSeconds() default 60;

    /**
     * Tiền tố phân biệt nhóm chức năng (mặc định: "rate_limit")
     */
    String keyPrefix() default "rate_limit";
}
