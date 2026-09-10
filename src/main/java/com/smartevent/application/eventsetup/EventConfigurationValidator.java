package com.smartevent.application.eventsetup;

import com.smartevent.common.enums.AreaType;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventArea;
import com.smartevent.modules.event.exception.EventException;
import com.smartevent.modules.event.repository.EventAreaRepository;
import com.smartevent.modules.event.repository.EventSeatRepository;
import com.smartevent.modules.event.repository.VenueRepository;
import com.smartevent.modules.event.service.EventConfigurationPolicy;
import com.smartevent.modules.ticketing.repository.TicketSalePhaseRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventConfigurationValidator implements EventConfigurationPolicy {
    private final EventAreaRepository areaRepository;
    private final EventSeatRepository seatRepository;
    private final VenueRepository venueRepository;
    private final TicketSalePhaseRepository phaseRepository;

    @Override
    public void validateAreaChange(EventArea area, int capacity, AreaType areaType) {
        int configured = phaseRepository.sumQuantityByEventAreaIdExcluding(area.getId(), null);
        long seats = seatRepository.countByEventAreaId(area.getId());
        if (capacity < configured || capacity < seats) {
            throw violation("Sức chứa không được nhỏ hơn số vé hoặc ghế đã cấu hình");
        }
        if (area.getAreaType() != areaType && (configured > 0 || seats > 0)) {
            throw violation("Không thể đổi kiểu khu vực khi đã cấu hình vé hoặc ghế");
        }
    }

    @Override
    public void validateVenueChange(UUID eventId, UUID venueId) {
        var areas = areaRepository.findByEventIdOrderBySortOrderAsc(eventId);
        if (venueId == null) {
            if (!areas.isEmpty()) throw violation("Không thể bỏ địa điểm khi đã cấu hình khu vực bán vé");
            return;
        }
        var venue = venueRepository.findById(venueId)
                .orElseThrow(() -> violation("Không tìm thấy địa điểm"));
        long capacity = areas.stream().mapToLong(EventArea::getCapacity).sum();
        if (venue.getCapacity() != null && capacity > venue.getCapacity()) {
            throw violation("Địa điểm mới không đủ sức chứa cho các khu vực đã cấu hình");
        }
    }

    @Override
    public void validatePublication(Event event) {
        validateVenueChange(event.getId(), event.getVenueId());
        for (EventArea area : areaRepository.findByEventIdOrderBySortOrderAsc(event.getId())) {
            validateAreaChange(area, area.getCapacity(), area.getAreaType());
            if (area.getAreaType() == AreaType.SEATED &&
                    phaseRepository.sumQuantityByEventAreaIdExcluding(area.getId(), null) > seatRepository.countByEventAreaId(area.getId())) {
                throw violation("Khu vực ngồi chưa có đủ ghế cho số vé được mở bán");
            }
        }
    }

    private EventException violation(String message) {
        return new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }
}
