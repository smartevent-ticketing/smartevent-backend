package com.smartevent.modules.ticketing.service;

import com.smartevent.common.enums.AreaType;
import com.smartevent.common.enums.EventStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventArea;
import com.smartevent.modules.event.repository.EventAreaRepository;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.ticketing.dto.request.TicketTypeRequest;
import com.smartevent.modules.ticketing.dto.response.TicketTypeResponse;
import com.smartevent.modules.ticketing.entity.TicketType;
import com.smartevent.modules.ticketing.exception.TicketingException;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import com.smartevent.modules.ticketing.service.impl.TicketTypeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketTypeServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventAreaRepository eventAreaRepository;

    @Mock
    private TicketTypeRepository ticketTypeRepository;

    @InjectMocks
    private TicketTypeServiceImpl ticketTypeService;

    private UUID eventId;
    private UUID areaId;
    private UUID organizerId;
    private UUID ticketTypeId;
    private Event sampleEvent;
    private EventArea sampleArea;
    private TicketType sampleTicketType;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        areaId = UUID.randomUUID();
        organizerId = UUID.randomUUID();
        ticketTypeId = UUID.randomUUID();

        sampleEvent = new Event();
        sampleEvent.setId(eventId);
        sampleEvent.setOrganizerId(organizerId);
        sampleEvent.setStatus(EventStatus.DRAFT);

        sampleArea = new EventArea(eventId, "Khán Đài A", AreaType.SEATED, 500, 1, "Mô tả khán đài");
        sampleArea.setId(areaId);

        sampleTicketType = new TicketType(eventId, areaId, "Vé VIP", "Mô tả vé VIP", "ACTIVE");
        sampleTicketType.setId(ticketTypeId);
    }

    @Test
    @DisplayName("Tạo loại vé thành công khi dữ liệu và quyền hợp lệ")
    void createTicketType_Success() {
        TicketTypeRequest request = new TicketTypeRequest(areaId, "Vé VIP", "Mô tả vé VIP", "ACTIVE");

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(sampleEvent));
        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.of(sampleArea));
        when(ticketTypeRepository.existsByEventIdAndName(eventId, "Vé VIP")).thenReturn(false);
        when(ticketTypeRepository.save(any(TicketType.class))).thenReturn(sampleTicketType);

        TicketTypeResponse response = ticketTypeService.createTicketType(eventId, organizerId, false, request);

        assertNotNull(response);
        assertEquals("Vé VIP", response.name());
        assertEquals("Khán Đài A", response.areaName());
        assertEquals(AreaType.SEATED, response.areaType());
        verify(ticketTypeRepository, times(1)).save(any(TicketType.class));
    }

    @Test
    @DisplayName("Tạo loại vé thành công khi là Admin dù không phải Owner")
    void createTicketType_Success_WhenAdmin() {
        UUID adminId = UUID.randomUUID();
        TicketTypeRequest request = new TicketTypeRequest(areaId, "Vé VIP", "Mô tả vé VIP", "ACTIVE");

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(sampleEvent));
        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.of(sampleArea));
        when(ticketTypeRepository.existsByEventIdAndName(eventId, "Vé VIP")).thenReturn(false);
        when(ticketTypeRepository.save(any(TicketType.class))).thenReturn(sampleTicketType);

        TicketTypeResponse response = ticketTypeService.createTicketType(eventId, adminId, true, request);

        assertNotNull(response);
        assertEquals("Vé VIP", response.name());
        verify(ticketTypeRepository, times(1)).save(any(TicketType.class));
    }

    @Test
    @DisplayName("Ném lỗi RESOURCE_NOT_FOUND khi eventId không tồn tại")
    void createTicketType_EventNotFound_ThrowsException() {
        TicketTypeRequest request = new TicketTypeRequest(areaId, "Vé VIP", "Mô tả", "ACTIVE");

        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        TicketingException ex = assertThrows(TicketingException.class, () ->
                ticketTypeService.createTicketType(eventId, organizerId, false, request)
        );
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Ném lỗi ACCESS_DENIED khi không phải chủ sở hữu sự kiện và không phải Admin")
    void createTicketType_AccessDenied_WhenNotOwner() {
        UUID strangerUserId = UUID.randomUUID();
        TicketTypeRequest request = new TicketTypeRequest(areaId, "Vé VIP", "Mô tả", "ACTIVE");

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(sampleEvent));

        TicketingException ex = assertThrows(TicketingException.class, () ->
                ticketTypeService.createTicketType(eventId, strangerUserId, false, request)
        );
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Ném lỗi BUSINESS_RULE_VIOLATION khi sự kiện đã PUBLISHED")
    void createTicketType_EventNotDraft_ThrowsException() {
        sampleEvent.setStatus(EventStatus.PUBLISHED);
        TicketTypeRequest request = new TicketTypeRequest(areaId, "Vé VIP", "Mô tả", "ACTIVE");

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(sampleEvent));

        TicketingException ex = assertThrows(TicketingException.class, () ->
                ticketTypeService.createTicketType(eventId, organizerId, false, request)
        );
        assertEquals(ErrorCode.BUSINESS_RULE_VIOLATION, ex.getErrorCode());
    }

    @Test
    @DisplayName("Ném lỗi RESOURCE_NOT_FOUND khi eventAreaId không tồn tại")
    void createTicketType_AreaNotFound_ThrowsException() {
        TicketTypeRequest request = new TicketTypeRequest(areaId, "Vé VIP", "Mô tả", "ACTIVE");

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(sampleEvent));
        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.empty());

        TicketingException ex = assertThrows(TicketingException.class, () ->
                ticketTypeService.createTicketType(eventId, organizerId, false, request)
        );
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Ném lỗi BUSINESS_RULE_VIOLATION khi khán đài không thuộc sự kiện này")
    void createTicketType_AreaNotBelongToEvent_ThrowsException() {
        EventArea otherEventArea = new EventArea(UUID.randomUUID(), "Khán Đài Khác", AreaType.STANDING, 1000, 1, "Mô tả");
        otherEventArea.setId(areaId);

        TicketTypeRequest request = new TicketTypeRequest(areaId, "Vé VIP", "Mô tả", "ACTIVE");

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(sampleEvent));
        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.of(otherEventArea));

        TicketingException ex = assertThrows(TicketingException.class, () ->
                ticketTypeService.createTicketType(eventId, organizerId, false, request)
        );
        assertEquals(ErrorCode.BUSINESS_RULE_VIOLATION, ex.getErrorCode());
    }

    @Test
    @DisplayName("Ném lỗi TICKET_TYPE_NAME_EXISTS khi tên loại vé đã tồn tại trong sự kiện")
    void createTicketType_DuplicateName_ThrowsException() {
        TicketTypeRequest request = new TicketTypeRequest(areaId, "Vé VIP", "Mô tả", "ACTIVE");

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(sampleEvent));
        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.of(sampleArea));
        when(ticketTypeRepository.existsByEventIdAndName(eventId, "Vé VIP")).thenReturn(true);

        TicketingException ex = assertThrows(TicketingException.class, () ->
                ticketTypeService.createTicketType(eventId, organizerId, false, request)
        );
        assertEquals(ErrorCode.TICKET_TYPE_NAME_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("Lấy danh sách loại vé theo eventId thành công")
    void getTicketTypesByEventId_Success() {
        when(eventRepository.existsById(eventId)).thenReturn(true);
        when(ticketTypeRepository.findByEventId(eventId)).thenReturn(List.of(sampleTicketType));
        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.of(sampleArea));

        List<TicketTypeResponse> result = ticketTypeService.getTicketTypesByEventId(eventId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Vé VIP", result.get(0).name());
        assertEquals("Khán Đài A", result.get(0).areaName());
    }

    @Test
    @DisplayName("Lấy danh sách loại vé theo areaId thành công")
    void getTicketTypesByAreaId_Success() {
        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.of(sampleArea));
        when(ticketTypeRepository.findByEventAreaId(areaId)).thenReturn(List.of(sampleTicketType));

        List<TicketTypeResponse> result = ticketTypeService.getTicketTypesByAreaId(areaId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Vé VIP", result.get(0).name());
    }

    @Test
    @DisplayName("Lấy chi tiết loại vé theo ID thành công")
    void getTicketTypeById_Success() {
        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(sampleTicketType));
        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.of(sampleArea));

        TicketTypeResponse result = ticketTypeService.getTicketTypeById(ticketTypeId);

        assertNotNull(result);
        assertEquals("Vé VIP", result.name());
        assertEquals("Khán Đài A", result.areaName());
    }

    @Test
    @DisplayName("Cập nhật loại vé thành công")
    void updateTicketType_Success() {
        TicketTypeRequest updateRequest = new TicketTypeRequest(areaId, "Vé VIP Hạng 1", "Mô tả mới", "ACTIVE");

        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(sampleTicketType));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(sampleEvent));
        when(ticketTypeRepository.existsByEventIdAndName(eventId, "Vé VIP Hạng 1")).thenReturn(false);
        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.of(sampleArea));
        when(ticketTypeRepository.save(any(TicketType.class))).thenReturn(sampleTicketType);

        TicketTypeResponse response = ticketTypeService.updateTicketType(ticketTypeId, organizerId, false, updateRequest);

        assertNotNull(response);
        verify(ticketTypeRepository, times(1)).save(any(TicketType.class));
    }

    @Test
    @DisplayName("Xóa loại vé thành công khi hợp lệ")
    void deleteTicketType_Success() {
        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(sampleTicketType));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(sampleEvent));

        ticketTypeService.deleteTicketType(ticketTypeId, organizerId, false);

        verify(ticketTypeRepository, times(1)).delete(sampleTicketType);
    }
}
