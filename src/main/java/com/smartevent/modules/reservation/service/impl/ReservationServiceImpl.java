package com.smartevent.modules.reservation.service.impl;

import com.smartevent.common.enums.*;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventArea;
import com.smartevent.modules.event.entity.EventSeat;
import com.smartevent.modules.event.repository.EventAreaRepository;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.repository.EventSeatRepository;
import com.smartevent.modules.reservation.dto.request.CreateReservationRequest;
import com.smartevent.modules.reservation.dto.request.ReservationItemRequest;
import com.smartevent.modules.reservation.dto.response.ReservationItemResponse;
import com.smartevent.modules.reservation.dto.response.ReservationResponse;
import com.smartevent.modules.reservation.entity.Reservation;
import com.smartevent.modules.reservation.entity.ReservationItem;
import com.smartevent.modules.reservation.exception.ReservationException;
import com.smartevent.modules.reservation.repository.ReservationItemRepository;
import com.smartevent.modules.reservation.repository.ReservationRepository;
import com.smartevent.modules.reservation.service.ReservationService;
import com.smartevent.modules.ticketing.entity.TicketSalePhase;
import com.smartevent.modules.ticketing.entity.TicketType;
import com.smartevent.modules.ticketing.repository.TicketSalePhaseRepository;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import com.smartevent.modules.ticketing.service.InventoryService;
import com.smartevent.modules.ticketing.service.UserSalePhaseCounterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationItemRepository reservationItemRepository;
    private final EventRepository eventRepository;
    private final EventAreaRepository eventAreaRepository;
    private final EventSeatRepository eventSeatRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketSalePhaseRepository ticketSalePhaseRepository;
    private final InventoryService inventoryService;
    private final UserSalePhaseCounterService userSalePhaseCounterService;

    @Override
    @Transactional
    public ReservationResponse createReservation(UUID userId, CreateReservationRequest request) {
        // 1. Kiểm tra Idempotency (Chống bấm đúp)
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            var existingRes = reservationRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existingRes.isPresent()) {
                log.info("Phát hiện request trùng lặp idempotencyKey: {}, trả về kết quả cũ", request.idempotencyKey());
                return buildReservationResponse(existingRes.get());
            }
        }

        // 2. Chặn đa phiên: Mỗi user chỉ có tối đa 1 phiên PENDING trên 1 sự kiện
        if (reservationRepository.existsByUserIdAndEventIdAndStatus(userId, request.eventId(), ReservationStatus.PENDING)) {
            throw new ReservationException(ErrorCode.RESERVATION_ALREADY_EXISTS,
                    "Bạn đang có một phiên giữ chỗ chưa hoàn tất cho sự kiện này. Vui lòng thanh toán hoặc hủy phiên cũ.");
        }

        // 3. Kiểm tra Sự kiện phải đang PUBLISHED
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sự kiện"));

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ReservationException(ErrorCode.EVENT_NOT_PUBLISHED, "Sự kiện hiện chưa mở bán vé");
        }

        // 4. Tạo Reservation trước (10 phút hết hạn)
        Instant expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES);
        Reservation reservation = new Reservation(userId, request.eventId(), expiresAt, request.idempotencyKey());
        Reservation savedReservation = reservationRepository.save(reservation);

        List<ReservationItem> savedItems = new ArrayList<>();
        List<ReservationItemResponse> itemResponses = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        Instant now = Instant.now();

        // 5. Duyệt qua từng vé được chọn và xác thực nghiệp vụ
        for (ReservationItemRequest itemReq : request.items()) {
            // 5.1. Kiểm tra Loại vé & Đợt bán
            TicketType ticketType = ticketTypeRepository.findById(itemReq.ticketTypeId())
                    .orElseThrow(() -> new ReservationException(ErrorCode.TICKET_TYPE_NOT_FOUND, "Không tìm thấy loại vé"));

            if (!ticketType.getEventId().equals(request.eventId())) {
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

                // 🔥 ATOMIC CONDITIONAL UPDATE: Chỉ chuyển AVAILABLE -> HELD nếu chưa ai chạm vào ghế này
                int updatedRows = eventSeatRepository.updateSeatStatusAtomic(seat.getId(), SeatStatus.AVAILABLE, SeatStatus.HELD);
                if (updatedRows == 0) {
                    throw new ReservationException(ErrorCode.SEAT_ALREADY_HELD,
                            String.format("Ghế %s đã có người khác nhanh tay chọn hoặc đã bán", seat.getSeatNumber()));
                }
                seatCode = seat.getSeatNumber();
            } else {
                // AreaType.STANDING
                if (itemReq.eventSeatId() != null) {
                    throw new ReservationException(ErrorCode.VALIDATION_ERROR, "Vé khu vực đứng không có số ghế cụ thể");
                }
            }

            // 5.3. Trừ kho tổng (Atomic) & Kiểm tra hạn mức User (Anti-Scalping)
            inventoryService.holdInventory(phase.getId(), itemReq.quantity());
            userSalePhaseCounterService.holdUserTickets(userId, phase.getId(), itemReq.quantity(), phase.getMaxPerUser());

            // 5.4. Lưu ReservationItem
            ReservationItem resItem = new ReservationItem(
                    savedReservation.getId(),
                    ticketType.getId(),
                    phase.getId(),
                    itemReq.eventSeatId(),
                    itemReq.quantity(),
                    phase.getPrice() // Lấy giá từ DB chính thống
            );
            ReservationItem savedItem = reservationItemRepository.save(resItem);
            savedItems.add(savedItem);

            totalAmount = totalAmount.add(savedItem.getTotalPrice());

            itemResponses.add(ReservationItemResponse.of(savedItem, ticketType.getName(), phase.getName(), seatCode));
        }

        log.info("Tạo phiên giữ vé thành công: ID {}, User {}, Tổng tiền {}", savedReservation.getId(), userId, totalAmount);
        return ReservationResponse.of(savedReservation, event.getName(), totalAmount, itemResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(UUID reservationId, UUID currentUserId, boolean isAdmin) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy phiên giữ chỗ"));

        if (!isAdmin && !reservation.getUserId().equals(currentUserId)) {
            throw new ReservationException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền xem phiên giữ chỗ này");
        }

        return buildReservationResponse(reservation);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getMyActiveReservation(UUID userId, UUID eventId) {
        Reservation reservation = reservationRepository.findByUserIdAndEventIdAndStatus(userId, eventId, ReservationStatus.PENDING)
                .orElseThrow(() -> new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "Không có phiên giữ chỗ nào đang hoạt động"));

        if (reservation.isExpired()) {
            throw new ReservationException(ErrorCode.RESERVATION_EXPIRED, "Phiên giữ chỗ đã hết hạn");
        }

        return buildReservationResponse(reservation);
    }

    @Override
    @Transactional
    public void cancelReservation(UUID reservationId, UUID currentUserId, boolean isAdmin) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy phiên giữ chỗ"));

        if (!isAdmin && !reservation.getUserId().equals(currentUserId)) {
            throw new ReservationException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền hủy phiên giữ chỗ này");
        }

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new ReservationException(ErrorCode.BUSINESS_RULE_VIOLATION, "Chỉ có thể hủy phiên giữ chỗ đang ở trạng thái Chờ thanh toán");
        }

        // 🔥 ATOMIC CAS: Chỉ chuyển PENDING -> CANCELLED nếu chưa bị hết hạn hay thanh toán
        int affected = reservationRepository.updateStatusAtomic(reservationId, ReservationStatus.PENDING, ReservationStatus.CANCELLED);
        if (affected == 1) {
            releaseReservationResources(reservation);
            log.info("Người dùng {} đã chủ động hủy phiên giữ chỗ {}", currentUserId, reservationId);
        } else {
            throw new ReservationException(ErrorCode.BUSINESS_RULE_VIOLATION, "Không thể hủy phiên giữ chỗ đã hết hạn hoặc đã xác nhận");
        }
    }

    @Override
    @Transactional
    public boolean confirmReservation(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null || reservation.isExpired()) {
            log.warn("Không thể xác nhận phiên giữ chỗ {}: không tồn tại hoặc đã quá hạn 10 phút", reservationId);
            return false;
        }

        // 🔥 ATOMIC CAS: Chỉ chuyển PENDING -> CONFIRMED nếu chưa bị Expiry Worker chuyển thành EXPIRED
        int affected = reservationRepository.updateStatusAtomic(reservationId, ReservationStatus.PENDING, ReservationStatus.CONFIRMED);
        if (affected == 0) {
            log.warn("CAS Confirm thất bại cho phiên giữ chỗ {}: trạng thái đã bị Expiry Worker chuyển sang EXPIRED hoặc CANCELLED", reservationId);
            return false;
        }

        // Chỉ duy nhất luồng thắng cuộc mới được chốt chuyển ghế HELD -> SOLD và trừ kho chính thức
        List<ReservationItem> items = reservationItemRepository.findByReservationId(reservationId);
        for (ReservationItem item : items) {
            inventoryService.confirmPurchase(item.getSalePhaseId(), item.getQuantity());
            userSalePhaseCounterService.confirmUserPurchase(reservation.getUserId(), item.getSalePhaseId(), item.getQuantity());

            if (item.getEventSeatId() != null) {
                eventSeatRepository.updateSeatStatusAtomic(item.getEventSeatId(), SeatStatus.HELD, SeatStatus.SOLD);
            }
        }
        log.info("Xác nhận thành công phiên giữ chỗ {}", reservationId);
        return true;
    }

    @Override
    @Transactional
    public void expireReservation(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy phiên giữ chỗ"));

        // 🔥 ATOMIC CAS: Chỉ chuyển PENDING -> EXPIRED nếu Payment Callback chưa kịp chuyển thành CONFIRMED
        int affected = reservationRepository.updateStatusAtomic(reservationId, ReservationStatus.PENDING, ReservationStatus.EXPIRED);
        if (affected == 1) {
            // Chỉ duy nhất Expiry Worker thắng cuộc mới được nhả kho và mở lại ghế AVAILABLE
            releaseReservationResources(reservation);
            log.info("Phiên giữ chỗ {} đã hết hạn 10 phút, tự động nhả vé", reservationId);
        } else {
            log.info("Phiên giữ chỗ {} không còn ở trạng thái PENDING (đã thanh toán hoặc đã hủy), bỏ qua nhả vé", reservationId);
        }
    }

    // --- Private Helper Methods ---

    private void releaseReservationResources(Reservation reservation) {
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

    private ReservationResponse buildReservationResponse(Reservation reservation) {
        Event event = eventRepository.findById(reservation.getEventId()).orElse(null);
        String eventName = event != null ? event.getName() : "Unknown Event";

        List<ReservationItem> items = reservationItemRepository.findByReservationId(reservation.getId());
        List<ReservationItemResponse> itemResponses = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (ReservationItem item : items) {
            TicketType ticketType = ticketTypeRepository.findById(item.getTicketTypeId()).orElse(null);
            String ticketTypeName = ticketType != null ? ticketType.getName() : "Unknown Ticket Type";

            TicketSalePhase phase = ticketSalePhaseRepository.findById(item.getSalePhaseId()).orElse(null);
            String phaseName = phase != null ? phase.getName() : "Unknown Phase";

            String seatCode = null;
            if (item.getEventSeatId() != null) {
                seatCode = eventSeatRepository.findById(item.getEventSeatId())
                        .map(EventSeat::getSeatNumber).orElse(null);
            }

            totalAmount = totalAmount.add(item.getTotalPrice());
            itemResponses.add(ReservationItemResponse.of(item, ticketTypeName, phaseName, seatCode));
        }

        return ReservationResponse.of(reservation, eventName, totalAmount, itemResponses);
    }
}