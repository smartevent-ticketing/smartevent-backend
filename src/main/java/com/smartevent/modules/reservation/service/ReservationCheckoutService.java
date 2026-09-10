package com.smartevent.modules.reservation.service;

import com.smartevent.common.enums.ReservationStatus;
import com.smartevent.modules.reservation.repository.ReservationItemRepository;
import com.smartevent.modules.reservation.repository.ReservationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal checkout contract: callers do not depend on reservation persistence entities. */
@Service
@RequiredArgsConstructor
public class ReservationCheckoutService {
    private final ReservationRepository reservationRepository;
    private final ReservationItemRepository reservationItemRepository;
    private final com.smartevent.modules.event.repository.EventRepository eventRepository;

    public record Item(UUID ticketTypeId, UUID salePhaseId, UUID eventSeatId,
                       int quantity, BigDecimal unitPrice, BigDecimal totalPrice) {}

    public record Snapshot(UUID id, UUID userId, ReservationStatus status, Instant expiresAt, List<Item> items) {
        public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    }

    @Transactional(readOnly = true)
    public Optional<Snapshot> findSnapshot(UUID reservationId) {
        return reservationRepository.findById(reservationId).map(this::snapshot);
    }

    /** Lock order: event, reservation. The event lock excludes cancellation and configuration changes. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Optional<Snapshot> lockSnapshot(UUID reservationId) {
        var eventId = reservationRepository.findEventIdById(reservationId);
        if (eventId.isEmpty()) return Optional.empty();
        var event = eventRepository.findByIdForShare(eventId.get()).orElseThrow();
        if (event.getStatus() != com.smartevent.common.enums.EventStatus.PUBLISHED) {
            throw new com.smartevent.modules.reservation.exception.ReservationException(
                    com.smartevent.common.error.ErrorCode.EVENT_NOT_PUBLISHED, "Sự kiện không còn nhận đặt vé");
        }
        return reservationRepository.findByIdForUpdate(reservationId).map(this::snapshot);
    }

    private Snapshot snapshot(com.smartevent.modules.reservation.entity.Reservation reservation) {
            UUID reservationId = reservation.getId();
            List<Item> items = reservationItemRepository.findByReservationId(reservationId).stream()
                    .map(item -> new Item(item.getTicketTypeId(), item.getSalePhaseId(), item.getEventSeatId(),
                            item.getQuantity(), item.getUnitPrice(), item.getTotalPrice())).toList();
            return new Snapshot(reservation.getId(), reservation.getUserId(), reservation.getStatus(), reservation.getExpiresAt(), items);
    }
}
