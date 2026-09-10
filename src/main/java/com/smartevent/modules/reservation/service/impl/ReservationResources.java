package com.smartevent.modules.reservation.service.impl;

import com.smartevent.common.enums.SeatStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.repository.EventSeatRepository;
import com.smartevent.modules.reservation.dto.request.ReservationItemRequest;
import com.smartevent.modules.reservation.entity.Reservation;
import com.smartevent.modules.reservation.entity.ReservationItem;
import com.smartevent.modules.reservation.exception.ReservationException;
import com.smartevent.modules.reservation.repository.ReservationItemRepository;
import com.smartevent.modules.ticketing.service.InventoryService;
import com.smartevent.modules.ticketing.service.UserSalePhaseCounterService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReservationResources {

    private final ReservationItemRepository reservationItemRepository;
    private final EventSeatRepository eventSeatRepository;
    private final InventoryService inventoryService;
    private final UserSalePhaseCounterService userSalePhaseCounterService;

    public void hold(UUID userId, ReservationItemValidator.Selection selection, ReservationItemRequest item) {
        if (item.eventSeatId() != null) {
            int affected = eventSeatRepository.updateSeatStatusAtomic(item.eventSeatId(), SeatStatus.AVAILABLE, SeatStatus.HELD);
            if (affected == 0) throw new ReservationException(ErrorCode.SEAT_ALREADY_HELD,
                    "Ghế " + selection.seatCode() + " đã được giữ hoặc đã bán");
        }
        inventoryService.holdInventory(selection.phase().getId(), item.quantity());
        userSalePhaseCounterService.holdUserTickets(userId, selection.phase().getId(), item.quantity(), selection.phase().getMaxPerUser());
    }

    public void confirm(Reservation reservation) {
        List<ReservationItem> items = reservationItemRepository.findByReservationId(reservation.getId());
        for (ReservationItem item : items) {
            inventoryService.confirmPurchase(item.getSalePhaseId(), item.getQuantity());
            userSalePhaseCounterService.confirmUserPurchase(reservation.getUserId(), item.getSalePhaseId(), item.getQuantity());

            if (item.getEventSeatId() != null) {
                eventSeatRepository.updateSeatStatusAtomic(item.getEventSeatId(), SeatStatus.HELD, SeatStatus.SOLD);
            }
        }

    }

    public void release(Reservation reservation) {
        List<ReservationItem> items = reservationItemRepository.findByReservationId(reservation.getId());
        for (ReservationItem item : items) {
            // Nhả kho tổng
            inventoryService.releaseHeldInventory(item.getSalePhaseId(), item.getQuantity());
            // Nhả hạn mức user
            userSalePhaseCounterService.releaseUserHeldTickets(reservation.getUserId(), item.getSalePhaseId(), item.getQuantity());
            // Mở lại ghế AVAILABLE nguyên tử HELD -> AVAILABLE
            if (item.getEventSeatId() != null) {
                eventSeatRepository.updateSeatStatusAtomic(item.getEventSeatId(), SeatStatus.HELD, SeatStatus.AVAILABLE);
            }
        }
    }
}
