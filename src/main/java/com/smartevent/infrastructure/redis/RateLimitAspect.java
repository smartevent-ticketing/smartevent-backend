package com.smartevent.infrastructure.redis;

import com.smartevent.common.error.BusinessException;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.common.ratelimit.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate redisTemplate;

    @Around("@annotation(rateLimit)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String clientIdentifier = resolveClientIdentifier();
        String methodName = joinPoint.getSignature().getName();
        String redisKey = String.format("%s:%s:%s", rateLimit.keyPrefix(), methodName, clientIdentifier);

        try {
            Long currentRequests = redisTemplate.opsForValue().increment(redisKey);
            if (currentRequests != null && currentRequests == 1) {
                redisTemplate.expire(redisKey, Duration.ofSeconds(rateLimit.durationInSeconds()));
            }

            if (currentRequests != null && currentRequests > rateLimit.limit()) {
                log.warn("Rate limit vượt ngưỡng cho client [{}], API [{}]. Số lần gọi: {}/{}",
                        clientIdentifier, methodName, currentRequests, rateLimit.limit());
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                        "Bạn đang gửi yêu cầu quá nhanh. Vui lòng thử lại sau " + rateLimit.durationInSeconds() + " giây.");
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception ex) {
            // Fail-open strategy: Nếu Redis gặp sự cố, hệ thống vẫn cho request đi tiếp để không làm gián đoạn người dùng
            log.error("Lỗi khi kiểm tra Redis Rate Limit: {}. Cho phép request đi tiếp.", ex.getMessage());
        }

        return joinPoint.proceed();
    }

    private String resolveClientIdentifier() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "system_internal";
        }

        HttpServletRequest request = attributes.getRequest();
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}
