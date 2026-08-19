package com.smartevent.modules.ticketing.service;

import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.ticketing.dto.response.UserSalePhaseCounterResponse;
import com.smartevent.modules.ticketing.entity.UserSalePhaseCounter;
import com.smartevent.modules.ticketing.exception.TicketingException;
import com.smartevent.modules.ticketing.repository.UserSalePhaseCounterRepository;
import com.smartevent.modules.ticketing.service.impl.UserSalePhaseCounterServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserSalePhaseCounterServiceTest {

    @Mock
    private UserSalePhaseCounterRepository userSalePhaseCounterRepository;

    @InjectMocks
    private UserSalePhaseCounterServiceImpl userSalePhaseCounterService;

    private UUID userId;
    private UUID salePhaseId;
    private UserSalePhaseCounter sampleCounter;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        salePhaseId = UUID.randomUUID();

        sampleCounter = new UserSalePhaseCounter(userId, salePhaseId);
        sampleCounter.setHeldQuantity(1);
        sampleCounter.setPurchasedQuantity(2);
        sampleCounter.setRefundedQuantity(0);
    }

    @Test
    @DisplayName("Lấy thông tin đếm vé của user thành công khi đã có trong DB")
    void getUserCounter_WhenExists_ReturnsResponse() {
        when(userSalePhaseCounterRepository.findByUserIdAndSalePhaseId(userId, salePhaseId))
                .thenReturn(Optional.of(sampleCounter));

        UserSalePhaseCounterResponse response = userSalePhaseCounterService.getUserCounter(userId, salePhaseId);

        assertNotNull(response);
        assertEquals(userId, response.userId());
        assertEquals(1, response.heldQuantity());
        assertEquals(2, response.purchasedQuantity());
        assertEquals(3, response.effectiveOccupiedQuantity());
    }

    @Test
    @DisplayName("Trả về mặc định 0 vé khi user chưa từng thao tác trong đợt bán")
    void getUserCounter_WhenNotExists_ReturnsDefaultZero() {
        when(userSalePhaseCounterRepository.findByUserIdAndSalePhaseId(userId, salePhaseId))
                .thenReturn(Optional.empty());

        UserSalePhaseCounterResponse response = userSalePhaseCounterService.getUserCounter(userId, salePhaseId);

        assertNotNull(response);
        assertEquals(0, response.heldQuantity());
        assertEquals(0, response.purchasedQuantity());
        assertEquals(0, response.effectiveOccupiedQuantity());
    }

    @Test
    @DisplayName("Bỏ qua kiểm tra hạn mức nếu đợt bán không giới hạn (maxPerUser == null)")
    void holdUserTickets_WhenMaxPerUserNull_SkipsCheck() {
        assertDoesNotThrow(() -> userSalePhaseCounterService.holdUserTickets(userId, salePhaseId, 2, null));

        verify(userSalePhaseCounterRepository, never()).upsertUserCounter(any(), any());
        verify(userSalePhaseCounterRepository, never()).atomicHoldUserQuantity(any(), any(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("Giữ vé thành công khi chưa vượt quá hạn mức max_per_user")
    void holdUserTickets_Success_WhenWithinLimit() {
        when(userSalePhaseCounterRepository.atomicHoldUserQuantity(eq(userId), eq(salePhaseId), eq(2), eq(4), any(Instant.class)))
                .thenReturn(1);

        assertDoesNotThrow(() -> userSalePhaseCounterService.holdUserTickets(userId, salePhaseId, 2, 4));

        verify(userSalePhaseCounterRepository, times(1)).upsertUserCounter(userId, salePhaseId);
        verify(userSalePhaseCounterRepository, times(1))
                .atomicHoldUserQuantity(eq(userId), eq(salePhaseId), eq(2), eq(4), any(Instant.class));
    }

    @Test
    @DisplayName("Ném lỗi MAX_PER_USER_EXCEEDED khi mua vượt quá hạn mức cho phép")
    void holdUserTickets_ExceedsLimit_ThrowsException() {
        when(userSalePhaseCounterRepository.atomicHoldUserQuantity(eq(userId), eq(salePhaseId), eq(3), eq(4), any(Instant.class)))
                .thenReturn(0);

        TicketingException ex = assertThrows(TicketingException.class, () ->
                userSalePhaseCounterService.holdUserTickets(userId, salePhaseId, 3, 4)
        );
        assertEquals(ErrorCode.MAX_PER_USER_EXCEEDED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Ném lỗi VALIDATION_ERROR khi số vé yêu cầu giữ <= 0")
    void holdUserTickets_InvalidQuantity_ThrowsException() {
        TicketingException ex = assertThrows(TicketingException.class, () ->
                userSalePhaseCounterService.holdUserTickets(userId, salePhaseId, 0, 4)
        );
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("Nhả vé tạm giữ của user thành công khi hủy đơn / hết 10 phút")
    void releaseUserHeldTickets_Success() {
        when(userSalePhaseCounterRepository.atomicReleaseUserHeldQuantity(eq(userId), eq(salePhaseId), eq(1), any(Instant.class)))
                .thenReturn(1);

        assertDoesNotThrow(() -> userSalePhaseCounterService.releaseUserHeldTickets(userId, salePhaseId, 1));

        verify(userSalePhaseCounterRepository, times(1))
                .atomicReleaseUserHeldQuantity(eq(userId), eq(salePhaseId), eq(1), any(Instant.class));
    }

    @Test
    @DisplayName("Chuyển vé từ held sang purchased khi thanh toán thành công")
    void confirmUserPurchase_Success() {
        when(userSalePhaseCounterRepository.atomicConfirmUserPurchase(eq(userId), eq(salePhaseId), eq(2), any(Instant.class)))
                .thenReturn(1);

        assertDoesNotThrow(() -> userSalePhaseCounterService.confirmUserPurchase(userId, salePhaseId, 2));

        verify(userSalePhaseCounterRepository, times(1))
                .atomicConfirmUserPurchase(eq(userId), eq(salePhaseId), eq(2), any(Instant.class));
    }

    @Test
    @DisplayName("Ném lỗi khi dữ liệu tạm giữ không khớp lúc xác nhận thanh toán")
    void confirmUserPurchase_Fail_ThrowsException() {
        when(userSalePhaseCounterRepository.atomicConfirmUserPurchase(eq(userId), eq(salePhaseId), eq(5), any(Instant.class)))
                .thenReturn(0);

        TicketingException ex = assertThrows(TicketingException.class, () ->
                userSalePhaseCounterService.confirmUserPurchase(userId, salePhaseId, 5)
        );
        assertEquals(ErrorCode.BUSINESS_RULE_VIOLATION, ex.getErrorCode());
    }

    @Test
    @DisplayName("Hoàn tiền vé cho user thành công (Tăng refunded_quantity)")
    void processUserRefund_Success() {
        when(userSalePhaseCounterRepository.atomicProcessUserRefund(eq(userId), eq(salePhaseId), eq(1), any(Instant.class)))
                .thenReturn(1);

        assertDoesNotThrow(() -> userSalePhaseCounterService.processUserRefund(userId, salePhaseId, 1));

        verify(userSalePhaseCounterRepository, times(1))
                .atomicProcessUserRefund(eq(userId), eq(salePhaseId), eq(1), any(Instant.class));
    }
}