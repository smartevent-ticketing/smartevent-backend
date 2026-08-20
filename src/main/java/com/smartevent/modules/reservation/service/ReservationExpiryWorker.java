package com.smartevent.modules.reservation.service;

import com.smartevent.common.enums.ReservationStatus;
import com.smartevent.modules.reservation.entity.Reservation;
import com.smartevent.modules.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryWorker {

    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    // Chạy định kỳ mỗi 30 giây một lần
    @Scheduled(fixedRate = 30000)
    public void scanAndExpireReservations() {
        List<Reservation> expiredReservations = reservationRepository.findByStatusAndExpiresAtBefore(
                ReservationStatus.PENDING, Instant.now()
        );

        if (!expiredReservations.isEmpty()) {
            log.info("Phát hiện {} phiên giữ chỗ đã quá hạn 10 phút, bắt đầu giải phóng tài nguyên...", expiredReservations.size());
            for (Reservation res : expiredReservations) {
                try {
                    reservationService.expireReservation(res.getId());
                } catch (Exception e) {
                    log.error("Lỗi khi giải phóng phiên giữ chỗ {}: {}", res.getId(), e.getMessage());
                }
            }
        }
    }
}