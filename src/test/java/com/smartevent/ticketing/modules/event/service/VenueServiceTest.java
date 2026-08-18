package com.smartevent.ticketing.modules.event.service;

import com.smartevent.ticketing.common.error.ErrorCode;
import com.smartevent.ticketing.modules.event.dto.request.VenueRequest;
import com.smartevent.ticketing.modules.event.dto.response.VenueResponse;
import com.smartevent.ticketing.modules.event.entity.Venue;
import com.smartevent.ticketing.modules.event.exception.VenueException;
import com.smartevent.ticketing.modules.event.repository.VenueRepository;
import com.smartevent.ticketing.modules.event.service.impl.VenueServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VenueServiceTest {

    @Mock
    private VenueRepository venueRepository;

    @InjectMocks
    private VenueServiceImpl venueService;

    @Test
    @DisplayName("Tạo địa điểm thành công - Lưu DB và trả về VenueResponse")
    void createVenue_Success() {
        VenueRequest request = new VenueRequest(
                "Nhà hát Lớn", "1 Tràng Tiền", "Hà Nội",
                BigDecimal.valueOf(21.0245), BigDecimal.valueOf(105.8581), 598
        );

        when(venueRepository.existsByNameAndCity("Nhà hát Lớn", "Hà Nội")).thenReturn(false);
        when(venueRepository.save(any(Venue.class))).thenAnswer(invocation -> {
            Venue v = invocation.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        VenueResponse response = venueService.createVenue(request);

        assertNotNull(response);
        assertEquals("Nhà hát Lớn", response.name());
        assertEquals("1 Tràng Tiền", response.address());
        assertEquals("Hà Nội", response.city());
        assertEquals(598, response.capacity());

        verify(venueRepository, times(1)).save(any(Venue.class));
    }

    @Test
    @DisplayName("Tạo địa điểm trùng tên + thành phố - Ném lỗi BUSINESS_RULE_VIOLATION")
    void createVenue_DuplicateNameAndCity_ThrowsBusinessRuleViolation() {
        VenueRequest request = new VenueRequest(
                "Nhà hát Lớn", "1 Tràng Tiền", "Hà Nội", null, null, null
        );

        when(venueRepository.existsByNameAndCity("Nhà hát Lớn", "Hà Nội")).thenReturn(true);

        VenueException exception = assertThrows(VenueException.class, () ->
                venueService.createVenue(request)
        );

        assertEquals(ErrorCode.BUSINESS_RULE_VIOLATION, exception.getErrorCode());
        verify(venueRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lấy danh sách địa điểm theo thành phố - Không phân biệt hoa/thường")
    void getVenuesByCity_Success() {
        Venue v = new Venue("Nhà hát Lớn", "1 Tràng Tiền", "Hà Nội", null, null, 500);
        v.setId(UUID.randomUUID());

        when(venueRepository.findByCityIgnoreCaseAndStatus("hà nội", "ACTIVE")).thenReturn(List.of(v));

        List<VenueResponse> responses = venueService.getVenuesByCity("hà nội");

        assertEquals(1, responses.size());
        assertEquals("Hà Nội", responses.get(0).city());
    }

    @Test
    @DisplayName("Cập nhật địa điểm thành công khi giữ nguyên tên và thành phố")
    void updateVenue_SameNameAndCity_AllowsUpdate() {
        UUID id = UUID.randomUUID();
        Venue existingVenue = new Venue("Nhà hát Lớn", "1 Tràng Tiền", "Hà Nội", null, null, 500);
        existingVenue.setId(id);

        VenueRequest updateRequest = new VenueRequest(
                "Nhà hát Lớn", "1 Tràng Tiền Mới", "Hà Nội", null, null, 600
        );

        when(venueRepository.findById(id)).thenReturn(Optional.of(existingVenue));
        when(venueRepository.existsByNameAndCity("Nhà hát Lớn", "Hà Nội")).thenReturn(true);
        when(venueRepository.save(any(Venue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VenueResponse response = venueService.updateVenue(id, updateRequest);

        assertNotNull(response);
        assertEquals("1 Tràng Tiền Mới", response.address());
        assertEquals(600, response.capacity());
    }

    @Test
    @DisplayName("Xóa địa điểm (Soft Delete) - Đổi status thành INACTIVE")
    void deleteVenue_Success() {
        UUID id = UUID.randomUUID();
        Venue venue = new Venue("Nhà hát Lớn", "1 Tràng Tiền", "Hà Nội", null, null, 500);
        venue.setId(id);
        venue.setStatus("ACTIVE");

        when(venueRepository.findById(id)).thenReturn(Optional.of(venue));

        venueService.deleteVenue(id);

        assertEquals("INACTIVE", venue.getStatus());
        verify(venueRepository, times(1)).save(venue);
    }
}
