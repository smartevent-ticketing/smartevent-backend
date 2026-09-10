package com.smartevent.modules.ticketing.service.impl;

import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.ticketing.dto.response.UserSalePhaseCounterResponse;
import com.smartevent.modules.ticketing.entity.UserSalePhaseCounter;
import com.smartevent.modules.ticketing.exception.TicketingException;
import com.smartevent.modules.ticketing.repository.UserSalePhaseCounterRepository;
import com.smartevent.modules.ticketing.service.UserSalePhaseCounterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSalePhaseCounterServiceImpl implements UserSalePhaseCounterService {

    private final UserSalePhaseCounterRepository userSalePhaseCounterRepository;

    @Override
    @Transactional(readOnly = true)
    public UserSalePhaseCounterResponse getUserCounter(UUID userId, UUID salePhaseId) {
        UserSalePhaseCounter counter = userSalePhaseCounterRepository.findByUserIdAndSalePhaseId(userId, salePhaseId)
                .orElseGet(() -> new UserSalePhaseCounter(userId, salePhaseId));
        return UserSalePhaseCounterResponse.from(counter);
    }

    @Override
    @Transactional
    public void holdUserTickets(UUID userId, UUID salePhaseId, int quantity, Integer maxPerUser) {
        if (quantity <= 0) {
            throw new TicketingException(ErrorCode.VALIDATION_ERROR, "Số lượng giữ vé phải lớn hơn 0");
        }

        // Unlimited phases still need a held balance for confirmation and release.
        int limit = maxPerUser != null && maxPerUser > 0 ? maxPerUser : Integer.MAX_VALUE;

        // 1. Đảm bảo bản ghi tồn tại bằng Native UPSERT chống duplicate key
        userSalePhaseCounterRepository.upsertUserCounter(userId, salePhaseId);

        // 2. Chạy Atomic Conditional UPDATE
        int updatedRows = userSalePhaseCounterRepository.atomicHoldUserQuantity(
                userId, salePhaseId, quantity, limit, Instant.now()
        );

        if (updatedRows == 0) {
            log.warn("Người dùng {} vượt quá giới hạn mua vé ({}) cho đợt bán {}", userId, maxPerUser, salePhaseId);
            throw new TicketingException(ErrorCode.MAX_PER_USER_EXCEEDED,
                    String.format("Bạn đã vượt quá số lượng vé tối đa được phép mua trong đợt này (Tối đa: %d vé)", maxPerUser));
        }

        log.info("Người dùng {} giữ thành công {} vé cho đợt bán {}", userId, quantity, salePhaseId);
    }

    @Override
    @Transactional
    public void releaseUserHeldTickets(UUID userId, UUID salePhaseId, int quantity) {
        if (quantity <= 0) return;

        int updatedRows = userSalePhaseCounterRepository.atomicReleaseUserHeldQuantity(
                userId, salePhaseId, quantity, Instant.now()
        );

        if (updatedRows == 0) {
            log.warn("Thất bại khi nhả vé tạm giữ của user {}: salePhaseId={}, quantity={}", userId, salePhaseId, quantity);
        } else {
            log.info("Đã nhả {} vé tạm giữ của người dùng {} trong đợt bán {}", quantity, userId, salePhaseId);
        }
    }

    @Override
    @Transactional
    public void confirmUserPurchase(UUID userId, UUID salePhaseId, int quantity) {
        if (quantity <= 0) return;

        int updatedRows = userSalePhaseCounterRepository.atomicConfirmUserPurchase(
                userId, salePhaseId, quantity, Instant.now()
        );

        if (updatedRows == 0) {
            log.error("Lỗi xác nhận mua vé của user {}: không đủ held_quantity để chuyển sang purchased", userId);
            throw new TicketingException(ErrorCode.BUSINESS_RULE_VIOLATION, "Không thể xác nhận mua vé do dữ liệu giữ vé không khớp");
        }

        log.info("Xác nhận mua thành công {} vé cho người dùng {} trong đợt bán {}", quantity, userId, salePhaseId);
    }

    @Override
    @Transactional
    public void processUserRefund(UUID userId, UUID salePhaseId, int quantity) {
        if (quantity <= 0) return;

        int updatedRows = userSalePhaseCounterRepository.atomicProcessUserRefund(
                userId, salePhaseId, quantity, Instant.now()
        );

        if (updatedRows == 0) {
            log.error("Lỗi hoàn tiền vé của user {}: không đủ purchased_quantity để hoàn", userId);
            throw new TicketingException(ErrorCode.BUSINESS_RULE_VIOLATION, "Không thể hoàn vé do số lượng đã mua không đủ");
        }

        log.info("Hoàn vé thành công {} vé cho người dùng {} trong đợt bán {}", quantity, userId, salePhaseId);
    }
}
