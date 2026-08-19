package com.smartevent.modules.ticketing.service;

import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.ticketing.dto.response.InventoryCounterResponse;
import com.smartevent.modules.ticketing.entity.InventoryCounter;
import com.smartevent.modules.ticketing.exception.TicketingException;
import com.smartevent.modules.ticketing.repository.InventoryCounterRepository;
import com.smartevent.modules.ticketing.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryCounterRepository inventoryCounterRepository;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private UUID eventId;
    private UUID areaId;
    private UUID ticketTypeId;
    private UUID salePhaseId;
    private InventoryCounter sampleCounter;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        areaId = UUID.randomUUID();
        ticketTypeId = UUID.randomUUID();
        salePhaseId = UUID.randomUUID();

        sampleCounter = new InventoryCounter(eventId, areaId, ticketTypeId, salePhaseId, 500);
    }

    @Test
    @DisplayName("Khởi tạo bộ đếm tồn kho thành công khi tạo đợt mở bán")
    void initCounter_Success() {
        when(inventoryCounterRepository.existsBySalePhaseId(salePhaseId)).thenReturn(false);
        when(inventoryCounterRepository.save(any(InventoryCounter.class))).thenReturn(sampleCounter);

        inventoryService.initCounter(eventId, areaId, ticketTypeId, salePhaseId, 500);

        verify(inventoryCounterRepository, times(1)).save(any(InventoryCounter.class));
    }

    @Test
    @DisplayName("Bỏ qua khởi tạo nếu bộ đếm của đợt bán đã tồn tại")
    void initCounter_AlreadyExists_DoesNotDuplicate() {
        when(inventoryCounterRepository.existsBySalePhaseId(salePhaseId)).thenReturn(true);

        inventoryService.initCounter(eventId, areaId, ticketTypeId, salePhaseId, 500);

        verify(inventoryCounterRepository, never()).save(any(InventoryCounter.class));
    }

    @Test
    @DisplayName("Lấy thông tin tồn kho theo salePhaseId thành công")
    void getCounterBySalePhaseId_Success() {
        when(inventoryCounterRepository.findBySalePhaseId(salePhaseId)).thenReturn(Optional.of(sampleCounter));

        InventoryCounterResponse response = inventoryService.getCounterBySalePhaseId(salePhaseId);

        assertNotNull(response);
        assertEquals(500, response.totalQuantity());
        assertEquals(0, response.heldQuantity());
        assertEquals(0, response.soldQuantity());
        assertEquals(500, response.availableQuantity());
    }

    @Test
    @DisplayName("Giữ vé thành công khi tồn kho khả dụng còn đủ (atomicHoldQuantity = 1)")
    void holdInventory_Success() {
        when(inventoryCounterRepository.atomicHoldQuantity(eq(salePhaseId), eq(2), any(Instant.class)))
                .thenReturn(1);

        assertDoesNotThrow(() -> inventoryService.holdInventory(salePhaseId, 2));

        verify(inventoryCounterRepository, times(1))
                .atomicHoldQuantity(eq(salePhaseId), eq(2), any(Instant.class));
    }

    @Test
    @DisplayName("Ném lỗi INVENTORY_NOT_ENOUGH khi tồn kho không đủ (atomicHoldQuantity = 0)")
    void holdInventory_NotEnoughStock_ThrowsException() {
        when(inventoryCounterRepository.atomicHoldQuantity(eq(salePhaseId), eq(10), any(Instant.class)))
                .thenReturn(0);

        TicketingException ex = assertThrows(TicketingException.class, () ->
                inventoryService.holdInventory(salePhaseId, 10)
        );
        assertEquals(ErrorCode.INVENTORY_NOT_ENOUGH, ex.getErrorCode());
    }

    @Test
    @DisplayName("Ném lỗi VALIDATION_ERROR khi số vé yêu cầu giữ <= 0")
    void holdInventory_InvalidQuantity_ThrowsException() {
        TicketingException ex = assertThrows(TicketingException.class, () ->
                inventoryService.holdInventory(salePhaseId, 0)
        );
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("Nhả vé tạm giữ thành công khi hủy đơn hoặc hết hạn 10 phút")
    void releaseHeldInventory_Success() {
        when(inventoryCounterRepository.atomicReleaseHeldQuantity(eq(salePhaseId), eq(2), any(Instant.class)))
                .thenReturn(1);

        assertDoesNotThrow(() -> inventoryService.releaseHeldInventory(salePhaseId, 2));

        verify(inventoryCounterRepository, times(1))
                .atomicReleaseHeldQuantity(eq(salePhaseId), eq(2), any(Instant.class));
    }

    @Test
    @DisplayName("Xác nhận thanh toán thành công (Chuyển vé từ held sang sold)")
    void confirmPurchase_Success() {
        when(inventoryCounterRepository.atomicConfirmPurchase(eq(salePhaseId), eq(4), any(Instant.class)))
                .thenReturn(1);

        assertDoesNotThrow(() -> inventoryService.confirmPurchase(salePhaseId, 4));

        verify(inventoryCounterRepository, times(1))
                .atomicConfirmPurchase(eq(salePhaseId), eq(4), any(Instant.class));
    }

    @Test
    @DisplayName("Hoàn tiền vé thành công (Giảm sold_quantity)")
    void processRefund_Success() {
        when(inventoryCounterRepository.atomicProcessRefund(eq(salePhaseId), eq(2), any(Instant.class)))
                .thenReturn(1);

        assertDoesNotThrow(() -> inventoryService.processRefund(salePhaseId, 2));

        verify(inventoryCounterRepository, times(1))
                .atomicProcessRefund(eq(salePhaseId), eq(2), any(Instant.class));
    }

    @Test
    @DisplayName("Ném lỗi khi cố tình giảm tổng số vé xuống thấp hơn (held + sold)")
    void updateTotalQuantity_Fail_ThrowsException() {
        when(inventoryCounterRepository.atomicUpdateTotalQuantity(eq(salePhaseId), eq(200), any(Instant.class)))
                .thenReturn(0);

        TicketingException ex = assertThrows(TicketingException.class, () ->
                inventoryService.updateTotalQuantity(salePhaseId, 200)
        );
        assertEquals(ErrorCode.BUSINESS_RULE_VIOLATION, ex.getErrorCode());
    }
}