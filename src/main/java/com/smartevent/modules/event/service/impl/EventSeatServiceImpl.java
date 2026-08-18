package com.smartevent.modules.event.service.impl;

import com.smartevent.common.api.PageResponse;
import com.smartevent.common.enums.AreaType;
import com.smartevent.common.enums.EventStatus;
import com.smartevent.common.enums.SeatStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.dto.request.EventSeatRequest;
import com.smartevent.modules.event.dto.request.GenerateSeatsRequest;
import com.smartevent.modules.event.dto.response.EventSeatResponse;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventArea;
import com.smartevent.modules.event.entity.EventSeat;
import com.smartevent.modules.event.exception.EventException;
import com.smartevent.modules.event.repository.EventAreaRepository;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.repository.EventSeatRepository;
import com.smartevent.modules.event.service.EventSeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventSeatServiceImpl implements EventSeatService {

    private final EventRepository eventRepository;
    private final EventAreaRepository eventAreaRepository;
    private final EventSeatRepository eventSeatRepository;

    @Override
    @Transactional
    public List<EventSeatResponse> generateSeats(UUID areaId, UUID currentUserId, boolean isAdmin, GenerateSeatsRequest request) {
        EventArea area = getAreaAndVerifyAccess(areaId, currentUserId, isAdmin);

        if (area.getAreaType() != AreaType.SEATED) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, "Chỉ có thể sinh ghế cho khu vực vé ngồi (SEATED)");
        }

        char startRow = request.fromRow().trim().toUpperCase().charAt(0);
        char endRow = request.toRow().trim().toUpperCase().charAt(0);

        if (startRow > endRow) {
            throw new EventException(ErrorCode.VALIDATION_ERROR, "Hàng bắt đầu phải đứng trước hoặc bằng hàng kết thúc");
        }

        int totalRows = (endRow - startRow + 1);
        int totalSeatsToGenerate = totalRows * request.seatsPerRow();
        long currentSeatCount = eventSeatRepository.countByEventAreaId(areaId);

        if (currentSeatCount + totalSeatsToGenerate > area.getCapacity()) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Số lượng ghế sinh ra (" + (currentSeatCount + totalSeatsToGenerate) +
                    ") vượt quá sức chứa tối đa của khu vực (" + area.getCapacity() + ")");
        }

        // Vòng lặp sinh ghế tự động
        List<EventSeat> seats = new ArrayList<>();
        for (char r = startRow; r <= endRow; r++) {
            for (int s = 1; s <= request.seatsPerRow(); s++) {
                String rowName = String.valueOf(r);
                String seatNumber = String.format("%02d", s);
                String label = rowName + "-" + seatNumber;

                if (!eventSeatRepository.existsByEventAreaIdAndRowNameAndSeatNumber(areaId, rowName, seatNumber)) {
                    seats.add(new EventSeat(areaId, rowName, seatNumber, label, SeatStatus.AVAILABLE, null));
                }
            }
        }

        List<EventSeat> savedSeats = eventSeatRepository.saveAll(seats);
        log.info("Đã sinh {} ghế tự động cho khu vực {} (ID: {})", savedSeats.size(), area.getName(), areaId);

        return savedSeats.stream()
                .map(EventSeatResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventSeatResponse> getSeatsByArea(UUID areaId, Pageable pageable) {
        if (!eventAreaRepository.existsById(areaId)) {
            throw new EventException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy khu vực");
        }

        Page<EventSeat> seatPage = eventSeatRepository.findByEventAreaId(areaId, pageable);
        Page<EventSeatResponse> responsePage = seatPage.map(EventSeatResponse::from);

        return PageResponse.from(responsePage);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventSeatResponse> getAvailableSeatsByArea(UUID areaId) {
        if (!eventAreaRepository.existsById(areaId)) {
            throw new EventException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy khu vực");
        }

        List<EventSeat> availableSeats = eventSeatRepository.findByEventAreaIdAndStatus(areaId, SeatStatus.AVAILABLE);

        return availableSeats.stream()
                .map(EventSeatResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public EventSeatResponse createSingleSeat(UUID areaId, UUID currentUserId, boolean isAdmin, EventSeatRequest request) {
        EventArea area = getAreaAndVerifyAccess(areaId, currentUserId, isAdmin);

        if (area.getAreaType() != AreaType.SEATED) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, "Chỉ có thể thêm ghế cho khu vực vé ngồi (SEATED)");
        }

        if (eventSeatRepository.existsByEventAreaIdAndRowNameAndSeatNumber(areaId, request.rowName(), request.seatNumber())) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Ghế " + request.rowName() + "-" + request.seatNumber() + " đã tồn tại trong khu vực");
        }

        long currentSeatCount = eventSeatRepository.countByEventAreaId(areaId);
        if (currentSeatCount + 1 > area.getCapacity()) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, "Khu vực đã đạt sức chứa tối đa (" + area.getCapacity() + " ghế)");
        }

        String label = request.label() != null && !request.label().isBlank()
                ? request.label()
                : request.rowName() + "-" + request.seatNumber();

        EventSeat seat = new EventSeat(areaId, request.rowName(), request.seatNumber(), label, SeatStatus.AVAILABLE, null);
        EventSeat savedSeat = eventSeatRepository.save(seat);
        log.info("Tạo ghế đơn lẻ: {} cho khu vực {}", savedSeat.getLabel(), area.getName());

        return EventSeatResponse.from(savedSeat);
    }

    @Override
    @Transactional
    public void deleteSeat(UUID seatId, UUID currentUserId, boolean isAdmin) {
        EventSeat seat = eventSeatRepository.findById(seatId)
                .orElseThrow(() -> new EventException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy ghế"));

        getAreaAndVerifyAccess(seat.getEventAreaId(), currentUserId, isAdmin);

        if (seat.getStatus() == SeatStatus.SOLD || seat.getStatus() == SeatStatus.HELD) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION, "Không thể xóa ghế đang được giữ hoặc đã bán");
        }

        eventSeatRepository.delete(seat);
        log.info("Đã xóa ghế ID: {}", seatId);
    }

    @Override
    @Transactional
    public void deleteAllSeatsInArea(UUID areaId, UUID currentUserId, boolean isAdmin) {
        EventArea area = getAreaAndVerifyAccess(areaId, currentUserId, isAdmin);

        long soldSeatsCount = eventSeatRepository.countByEventAreaIdAndStatus(areaId, SeatStatus.SOLD);
        long heldSeatsCount = eventSeatRepository.countByEventAreaIdAndStatus(areaId, SeatStatus.HELD);

        if (soldSeatsCount > 0 || heldSeatsCount > 0) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Không thể xóa toàn bộ ghế vì có " + (soldSeatsCount + heldSeatsCount) + " ghế đang được giữ hoặc đã bán");
        }

        eventSeatRepository.deleteByEventAreaId(areaId);
        log.warn("Đã xóa toàn bộ sơ đồ ghế của khu vực: {} (ID: {})", area.getName(), areaId);
    }

    private EventArea getAreaAndVerifyAccess(UUID areaId, UUID currentUserId, boolean isAdmin) {
        EventArea area = eventAreaRepository.findById(areaId)
                .orElseThrow(() -> new EventException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy khu vực"));

        Event event = eventRepository.findById(area.getEventId())
                .orElseThrow(() -> new EventException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sự kiện"));

        if (!isAdmin && !event.getOrganizerId().equals(currentUserId)) {
            throw new EventException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền quản lý ghế của sự kiện này");
        }

        if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.PENDING_APPROVAL) {
            throw new EventException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Chỉ có thể chỉnh sửa sơ đồ ghế khi sự kiện ở trạng thái Nháp hoặc Chờ duyệt");
        }

        return area;
    }
}

