package com.smartevent.modules.ticketing.service;

import com.smartevent.common.enums.AreaType;
import com.smartevent.common.enums.EventStatus;
import com.smartevent.common.enums.SalePhaseStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventArea;
import com.smartevent.modules.event.repository.EventAreaRepository;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.service.EventAccessPolicy;
import com.smartevent.modules.ticketing.dto.request.TicketPhaseRuleRequest;
import com.smartevent.modules.ticketing.dto.request.TicketSalePhaseRequest;
import com.smartevent.modules.ticketing.dto.response.TicketPhaseRuleResponse;
import com.smartevent.modules.ticketing.dto.response.TicketSalePhaseResponse;
import com.smartevent.modules.ticketing.entity.TicketPhaseRule;
import com.smartevent.modules.ticketing.entity.TicketSalePhase;
import com.smartevent.modules.ticketing.entity.TicketType;
import com.smartevent.modules.ticketing.exception.TicketingException;
import com.smartevent.modules.ticketing.repository.TicketPhaseRuleRepository;
import com.smartevent.modules.ticketing.repository.TicketSalePhaseRepository;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import com.smartevent.modules.ticketing.service.impl.TicketSalePhaseServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketSalePhaseServiceTest {

    @Mock
    private TicketSalePhaseRepository ticketSalePhaseRepository;

    @Mock
    private TicketTypeRepository ticketTypeRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventAreaRepository eventAreaRepository;

    @Mock
    private TicketPhaseRuleRepository ticketPhaseRuleRepository;

    @Mock
    private InventoryService inventoryService;

    private TicketSalePhaseServiceImpl ticketSalePhaseService;

    @BeforeEach
    void composeServices() {
        ticketSalePhaseService = new TicketSalePhaseServiceImpl(
                new EventAccessPolicy(),
                ticketTypeRepository,
                eventRepository,
                eventAreaRepository,
                ticketSalePhaseRepository,
                ticketPhaseRuleRepository,
                inventoryService);
    }

    private UUID eventId;
    private UUID areaId;
    private UUID organizerId;
    private UUID ticketTypeId;
    private UUID phaseId;

    private Event sampleEvent;
    private EventArea sampleArea;
    private TicketType sampleTicketType;
    private TicketSalePhase samplePhase;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        areaId = UUID.randomUUID();
        organizerId = UUID.randomUUID();
        ticketTypeId = UUID.randomUUID();
        phaseId = UUID.randomUUID();

        sampleEvent = new Event();
        sampleEvent.setId(eventId);
        sampleEvent.setOrganizerId(organizerId);
        sampleEvent.setStatus(EventStatus.DRAFT);

        sampleArea = new EventArea(eventId, "Khán Đài A", AreaType.SEATED, 1000, 1, "Mô tả");
        sampleArea.setId(areaId);

        sampleTicketType = new TicketType(eventId, areaId, "Vé VIP", "Mô tả", "ACTIVE");
        sampleTicketType.setId(ticketTypeId);

        Instant now = Instant.now();
        samplePhase = new TicketSalePhase(
                ticketTypeId, "Early Bird", BigDecimal.valueOf(500000), 200,
                now.plus(1, ChronoUnit.DAYS), now.plus(5, ChronoUnit.DAYS),
                4, 2, SalePhaseStatus.DRAFT
        );
        samplePhase.setId(phaseId);
    }

    @Test
    @DisplayName("Tạo đợt mở bán thành công khi dữ liệu hợp lệ")
    void createSalePhase_Success() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = Instant.now().plus(5, ChronoUnit.DAYS);
        TicketSalePhaseRequest request = new TicketSalePhaseRequest(
                "Early Bird", BigDecimal.valueOf(500000), 200, start, end, 4, 2, SalePhaseStatus.DRAFT
        );

        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(sampleTicketType));
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(sampleEvent));
        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.of(sampleArea));
        when(ticketSalePhaseRepository.sumQuantityByEventAreaIdExcluding(areaId, null)).thenReturn(0);
        when(ticketSalePhaseRepository.save(any(TicketSalePhase.class))).thenReturn(samplePhase);

        TicketSalePhaseResponse response = ticketSalePhaseService.createSalePhase(ticketTypeId, organizerId, false, request);

        assertNotNull(response);
        assertEquals("Early Bird", response.name());
        assertEquals("Vé VIP", response.ticketTypeName());
        verify(ticketSalePhaseRepository, times(1)).save(any(TicketSalePhase.class));

        // ✅ VERIFY XEM CÓ TỰ ĐỘNG KHỞI TẠO TỒN KHO KHÔNG:
        verify(inventoryService, times(1)).initCounter(eq(eventId), eq(areaId), eq(ticketTypeId), any(), eq(200));
    }

    @Test
    @DisplayName("Ném lỗi SALE_PHASE_INVALID_TIME khi thời gian kết thúc trước thời gian bắt đầu")
    void createSalePhase_InvalidTime_ThrowsException() {
        Instant start = Instant.now().plus(5, ChronoUnit.DAYS);
        Instant end = Instant.now().plus(1, ChronoUnit.DAYS); // Sai: end < start
        TicketSalePhaseRequest request = new TicketSalePhaseRequest(
                "Early Bird", BigDecimal.valueOf(500000), 200, start, end, 4, 2, SalePhaseStatus.DRAFT
        );

        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(sampleTicketType));
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(sampleEvent));

        TicketingException ex = assertThrows(TicketingException.class, () ->
                ticketSalePhaseService.createSalePhase(ticketTypeId, organizerId, false, request)
        );
        assertEquals(ErrorCode.SALE_PHASE_INVALID_TIME, ex.getErrorCode());
    }

    @Test
    @DisplayName("Ném lỗi PHASE_CAPACITY_EXCEEDED khi tổng vé vượt quá sức chứa khán đài")
    void createSalePhase_CapacityExceeded_ThrowsException() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = Instant.now().plus(5, ChronoUnit.DAYS);
        TicketSalePhaseRequest request = new TicketSalePhaseRequest(
                "Regular", BigDecimal.valueOf(1000000), 600, start, end, 4, 2, SalePhaseStatus.DRAFT
        );

        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(sampleTicketType));
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(sampleEvent));
        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.of(sampleArea)); // capacity = 1000
        when(ticketSalePhaseRepository.sumQuantityByEventAreaIdExcluding(areaId, null)).thenReturn(500); // 500 + 600 = 1100 > 1000

        TicketingException ex = assertThrows(TicketingException.class, () ->
                ticketSalePhaseService.createSalePhase(ticketTypeId, organizerId, false, request)
        );
        assertEquals(ErrorCode.PHASE_CAPACITY_EXCEEDED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Ném lỗi ACCESS_DENIED khi user không phải chủ sở hữu sự kiện")
    void createSalePhase_AccessDenied_WhenNotOwner() {
        UUID strangerId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = Instant.now().plus(5, ChronoUnit.DAYS);
        TicketSalePhaseRequest request = new TicketSalePhaseRequest(
                "Early Bird", BigDecimal.valueOf(500000), 200, start, end, 4, 2, SalePhaseStatus.DRAFT
        );

        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(sampleTicketType));
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(sampleEvent));

        TicketingException ex = assertThrows(TicketingException.class, () ->
                ticketSalePhaseService.createSalePhase(ticketTypeId, strangerId, false, request)
        );
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Chuyển trạng thái đợt bán thành công khi đúng State Machine")
    void updateStatus_ValidTransition_Success() {
        when(ticketSalePhaseRepository.findById(phaseId)).thenReturn(Optional.of(samplePhase)); // status DRAFT
        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(sampleTicketType));
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(sampleEvent));
        when(ticketSalePhaseRepository.save(any(TicketSalePhase.class))).thenReturn(samplePhase);

        TicketSalePhaseResponse response = ticketSalePhaseService.updateStatus(phaseId, organizerId, false, SalePhaseStatus.ACTIVE);

        assertNotNull(response);
        verify(ticketSalePhaseRepository, times(1)).save(samplePhase);
    }

    @Test
    @DisplayName("Ném lỗi khi chuyển trạng thái vi phạm State Machine (CLOSED sang ACTIVE)")
    void updateStatus_InvalidTransition_ThrowsException() {
        samplePhase.setStatus(SalePhaseStatus.CLOSED);

        when(ticketSalePhaseRepository.findById(phaseId)).thenReturn(Optional.of(samplePhase));
        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(sampleTicketType));
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(sampleEvent));

        TicketingException ex = assertThrows(TicketingException.class, () ->
                ticketSalePhaseService.updateStatus(phaseId, organizerId, false, SalePhaseStatus.ACTIVE)
        );
        assertEquals(ErrorCode.BUSINESS_RULE_VIOLATION, ex.getErrorCode());
    }

    @Test
    @DisplayName("Ném lỗi khi cố tình xóa đợt bán đang ACTIVE")
    void deleteSalePhase_Active_ThrowsException() {
        samplePhase.setStatus(SalePhaseStatus.ACTIVE);

        when(ticketSalePhaseRepository.findById(phaseId)).thenReturn(Optional.of(samplePhase));
        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(sampleTicketType));
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(sampleEvent));

        TicketingException ex = assertThrows(TicketingException.class, () ->
                ticketSalePhaseService.deleteSalePhase(phaseId, organizerId, false)
        );
        assertEquals(ErrorCode.BUSINESS_RULE_VIOLATION, ex.getErrorCode());
    }

    @Test
    @DisplayName("Thêm quy tắc mới cho đợt bán thành công")
    void addRule_Success() {
        TicketPhaseRuleRequest request = new TicketPhaseRuleRequest("ACCESS_CODE", "VIP2026");
        TicketPhaseRule rule = new TicketPhaseRule(phaseId, "ACCESS_CODE", "VIP2026");

        when(ticketSalePhaseRepository.findById(phaseId)).thenReturn(Optional.of(samplePhase));
        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(sampleTicketType));
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(sampleEvent));
        when(ticketPhaseRuleRepository.existsBySalePhaseIdAndRuleType(phaseId, "ACCESS_CODE")).thenReturn(false);
        when(ticketPhaseRuleRepository.save(any(TicketPhaseRule.class))).thenReturn(rule);

        TicketPhaseRuleResponse response = ticketSalePhaseService.addRule(phaseId, organizerId, false, request);

        assertNotNull(response);
        assertEquals("ACCESS_CODE", response.ruleType());
        assertEquals("VIP2026", response.ruleValue());
    }

    @Test
    @DisplayName("Ném lỗi RULE_ALREADY_EXISTS khi thêm trùng loại quy tắc")
    void addRule_DuplicateRuleType_ThrowsException() {
        TicketPhaseRuleRequest request = new TicketPhaseRuleRequest("ACCESS_CODE", "VIP2026");

        when(ticketSalePhaseRepository.findById(phaseId)).thenReturn(Optional.of(samplePhase));
        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(sampleTicketType));
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(sampleEvent));
        when(ticketPhaseRuleRepository.existsBySalePhaseIdAndRuleType(phaseId, "ACCESS_CODE")).thenReturn(true);

        TicketingException ex = assertThrows(TicketingException.class, () ->
                ticketSalePhaseService.addRule(phaseId, organizerId, false, request)
        );
        assertEquals(ErrorCode.RULE_ALREADY_EXISTS, ex.getErrorCode());
    }
}
