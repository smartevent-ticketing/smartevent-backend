package com.smartevent.common.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 1. @Target: Khai báo Annotation này được phép đặt ở đâu?
//    -> PARAMETER: Được đặt trước tham số trong hàm (Controller method)
@Target({ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})

// 2. @Retention: Annotation này tồn tại đến lúc nào?
//    -> RUNTIME: Tồn tại cả khi ứng dụng đang chạy để Spring quét và tiêm dữ liệu
@Retention(RetentionPolicy.RUNTIME)

// 3. @Documented: Cho phép xuất hiện trong tài liệu JavaDoc
@Documented

// 4. TRỌNG TÂM CỦA TOÀN BỘ LOGIC:
//    Kế thừa toàn bộ sức mạnh của @AuthenticationPrincipal từ Spring Security
@AuthenticationPrincipal
public @interface CurrentUser {
}
