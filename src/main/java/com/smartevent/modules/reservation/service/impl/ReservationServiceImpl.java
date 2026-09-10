package com.smartevent.modules.reservation.service.impl;

import com.smartevent.common.enums.EventStatus;
import com.smartevent.common.enums.ReservationStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.repository.EventRepository;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ReservationItemValidator reservationItemValidator;
    private final ReservationResources reservationResources;
    private final ReservationQueryService reservationQueryService;
    private final ReservationRepository reservationRepository;
    private final ReservationItemRepository reservationItemRepository;
    private final EventRepository eventRepository;

    @Override
    @Transactional
    public ReservationResponse createReservation(UUID userId, CreateReservationRequest request) {
        // 1. Kiểm tra Idempotency (Chống bấm đúp)
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            var existingRes = reservationRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existingRes.isPresent()) {
                if (!existingRes.get().getUserId().equals(userId) || !existingRes.get().getEventId().equals(request.eventId())) {
                    throw new ReservationException(ErrorCode.ACCESS_DENIED, "Mã yêu cầu giữ chỗ không thuộc phiên này");
                }
                log.info("Phát hiện request trùng lặp idempotencyKey: {}, trả về kết quả cũ", request.idempotencyKey());
                return reservationQueryService.toResponse(existingRes.get());
            }
        }

        // 2. Chặn đa phiên: Mỗi user chỉ có tối đa 1 phiên PENDING trên 1 sự kiện
        if (reservationRepository.existsByUserIdAndEventIdAndStatus(userId, request.eventId(), ReservationStatus.PENDING)) {
            throw new ReservationException(ErrorCode.RESERVATION_ALREADY_EXISTS,
                    "Bạn đang có một phiên giữ chỗ chưa hoàn tất cho sự kiện này. Vui lòng thanh toán hoặc hủy phiên cũ.");
        }

        // 3. Kiểm tra Sự kiện phải đang PUBLISHED
        Event event = eventRepository.findByIdForShare(request.eventId())
                .orElseThrow(() -> new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sự kiện"));

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ReservationException(ErrorCode.EVENT_NOT_PUBLISHED, "Sự kiện hiện chưa mở bán vé");
        }

        var selections = reservationItemValidator.validateAll(request.eventId(), request.items(), Instant.now());
        // 4. Tạo Reservation trước (10 phút hết hạn)
        Instant expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES);
        Reservation reservation = new Reservation(userId, request.eventId(), expiresAt, request.idempotencyKey());
        Reservation savedReservation = reservationRepository.save(reservation);

        List<ReservationItemResponse> itemResponses = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        Instant now = Instant.now();

        // 5. Duyệt qua từng vé được chọn và xác thực nghiệp vụ
        for (int itemIndex = 0; itemIndex < request.items().size(); itemIndex++) {
            ReservationItemRequest itemReq = request.items().get(itemIndex);
            ReservationItemValidator.Selection selection = selections.get(itemIndex);
            reservationResources.hold(userId, selection, itemReq);
            TicketType ticketType = selection.ticketType();
            TicketSalePhase phase = selection.phase();
            String seatCode = selection.seatCode();

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

        return reservationQueryService.toResponse(reservation);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getMyActiveReservation(UUID userId, UUID eventId) {
        Reservation reservation = reservationRepository.findByUserIdAndEventIdAndStatus(userId, eventId, ReservationStatus.PENDING)
                .orElseThrow(() -> new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "Không có phiên giữ chỗ nào đang hoạt động"));

        if (reservation.isExpired()) {
            throw new ReservationException(ErrorCode.RESERVATION_EXPIRED, "Phiên giữ chỗ đã hết hạn");
        }

        return reservationQueryService.toResponse(reservation);
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
            reservationResources.release(reservation);
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
        if (eventRepository.findByIdForShare(reservation.getEventId())
                .filter(event -> event.getStatus() == EventStatus.PUBLISHED).isEmpty()) return false;

        // 🔥 ATOMIC CAS: Chỉ chuyển PENDING -> CONFIRMED nếu chưa bị Expiry Worker chuyển thành EXPIRED
        int affected = reservationRepository.updateStatusAtomic(reservationId, ReservationStatus.PENDING, ReservationStatus.CONFIRMED);
        if (affected == 0) {
            log.warn("CAS Confirm thất bại cho phiên giữ chỗ {}: trạng thái đã bị Expiry Worker chuyển sang EXPIRED hoặc CANCELLED", reservationId);
            return false;
        }

        // Chỉ duy nhất luồng thắng cuộc mới được chốt chuyển ghế HELD -> SOLD và trừ kho chính thức
        reservationResources.confirm(reservation);
        log.info("Xác nhận thành công phiên giữ chỗ {}", reservationId);
        return true;
    }

    @Override
    @Transactional
    public boolean isPayable(UUID reservationId) {
        return reservationRepository.findById(reservationId)
                .filter(r -> r.getStatus() == ReservationStatus.PENDING && !r.isExpired())
                .flatMap(r -> eventRepository.findByIdForShare(r.getEventId()))
                .filter(event -> event.getStatus() == EventStatus.PUBLISHED).isPresent();
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void cancelPendingForEvent(UUID eventId) {
        for (Reservation reservation : reservationRepository.findByEventIdAndStatus(eventId, ReservationStatus.PENDING)) {
            if (reservationRepository.updateStatusAtomic(reservation.getId(), ReservationStatus.PENDING, ReservationStatus.CANCELLED) == 1) {
                reservationResources.release(reservation);
            }
        }
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
            reservationResources.release(reservation);
            log.info("Phiên giữ chỗ {} đã hết hạn 10 phút, tự động nhả vé", reservationId);
        } else {
            log.info("Phiên giữ chỗ {} không còn ở trạng thái PENDING (đã thanh toán hoặc đã hủy), bỏ qua nhả vé", reservationId);
        }
    }

}
