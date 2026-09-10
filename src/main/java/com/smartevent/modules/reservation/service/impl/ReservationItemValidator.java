package com.smartevent.modules.reservation.service.impl;

import com.smartevent.common.enums.AreaType;
import com.smartevent.common.enums.SalePhaseStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.entity.EventArea;
import com.smartevent.modules.event.entity.EventSeat;
import com.smartevent.modules.event.repository.EventAreaRepository;
import com.smartevent.modules.event.repository.EventSeatRepository;
import com.smartevent.modules.reservation.dto.request.ReservationItemRequest;
import com.smartevent.modules.reservation.exception.ReservationException;
import com.smartevent.modules.ticketing.entity.TicketSalePhase;
import com.smartevent.modules.ticketing.entity.TicketType;
import com.smartevent.modules.ticketing.repository.TicketSalePhaseRepository;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReservationItemValidator {

    private final EventAreaRepository eventAreaRepository;
    private final EventSeatRepository eventSeatRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketSalePhaseRepository ticketSalePhaseRepository;

    public record Selection(TicketType ticketType, TicketSalePhase phase, String seatCode) {}

    public List<Selection> validateAll(UUID eventId, List<ReservationItemRequest> items, Instant now) {
        List<Selection> selections = items.stream().map(item -> validate(eventId, item, now)).toList();
        Map<UUID, Long> quantities = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            TicketSalePhase phase = selections.get(i).phase();
            long quantity = quantities.merge(phase.getId(), items.get(i).quantity().longValue(), Long::sum);
            if (quantity > phase.getMaxPerOrder()) {
                throw new ReservationException(ErrorCode.MAX_PER_ORDER_EXCEEDED,
                        "Tổng số vé trong đơn vượt giới hạn " + phase.getMaxPerOrder() + " vé của đợt bán");
            }
        }
        return selections;
    }

    public Selection validate(UUID eventId, ReservationItemRequest itemReq, Instant now) {
            // 5.1. Kiểm tra Loại vé & Đợt bán
            TicketType ticketType = ticketTypeRepository.findById(itemReq.ticketTypeId())
                    .orElseThrow(() -> new ReservationException(ErrorCode.TICKET_TYPE_NOT_FOUND, "Không tìm thấy loại vé"));

            if (!ticketType.getEventId().equals(eventId)) {
                throw new ReservationException(ErrorCode.BUSINESS_RULE_VIOLATION, "Loại vé không thuộc sự kiện này");
            }

            TicketSalePhase phase = ticketSalePhaseRepository.findById(itemReq.salePhaseId())
                    .orElseThrow(() -> new ReservationException(ErrorCode.SALE_PHASE_NOT_FOUND, "Không tìm thấy đợt mở bán"));

            if (!phase.getTicketTypeId().equals(ticketType.getId())) {
                throw new ReservationException(ErrorCode.BUSINESS_RULE_VIOLATION, "Đợt mở bán không thuộc loại vé này");
            }

            if (phase.getStatus() != SalePhaseStatus.ACTIVE) {
                throw new ReservationException(ErrorCode.SALE_PHASE_NOT_ACTIVE, "Đợt mở bán chưa kích hoạt hoặc đã tạm dừng");
            }

            if (now.isBefore(phase.getSaleStartAt()) || now.isAfter(phase.getSaleEndAt())) {
                throw new ReservationException(ErrorCode.SALE_PHASE_CLOSED, "Đợt mở bán đã kết thúc hoặc chưa đến giờ");
            }

            if (itemReq.quantity() > phase.getMaxPerOrder()) {
                throw new ReservationException(ErrorCode.MAX_PER_ORDER_EXCEEDED,
                        String.format("Số lượng vé vượt quá giới hạn cho phép trên một đơn hàng (Tối đa: %d vé)", phase.getMaxPerOrder()));
            }

            // 5.2. Phân nhánh vé Đứng (STANDING) vs vé Ngồi (SEATED)
            EventArea area = eventAreaRepository.findById(ticketType.getEventAreaId())
                    .orElseThrow(() -> new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy khu vực khán đài"));

            String seatCode = null;

            if (area.getAreaType() == AreaType.SEATED) {
                if (itemReq.eventSeatId() == null) {
                    throw new ReservationException(ErrorCode.VALIDATION_ERROR, "Vé khu vực ngồi bắt buộc phải chọn ghế cụ thể");
                }
                if (itemReq.quantity() != 1) {
                    throw new ReservationException(ErrorCode.VALIDATION_ERROR, "Mỗi ghế ngồi chỉ được đặt số lượng là 1 vé");
                }

                EventSeat seat = eventSeatRepository.findById(itemReq.eventSeatId())
                        .orElseThrow(() -> new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy ghế ngồi"));

                if (!seat.getEventAreaId().equals(area.getId())) {
                    throw new ReservationException(ErrorCode.BUSINESS_RULE_VIOLATION, "Ghế không thuộc khán đài của loại vé này");
                }

                seatCode = seat.getSeatNumber();
            } else {
                // AreaType.STANDING
                if (itemReq.eventSeatId() != null) {
                    throw new ReservationException(ErrorCode.VALIDATION_ERROR, "Vé khu vực đứng không có số ghế cụ thể");
                }
            }

        return new Selection(ticketType, phase, seatCode);
    }
}
