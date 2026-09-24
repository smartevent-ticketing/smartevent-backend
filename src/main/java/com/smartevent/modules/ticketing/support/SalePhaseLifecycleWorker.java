package com.smartevent.modules.ticketing.support;

import com.smartevent.common.enums.EventStatus;
import com.smartevent.common.enums.SalePhaseStatus;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.ticketing.entity.InventoryCounter;
import com.smartevent.modules.ticketing.entity.TicketSalePhase;
import com.smartevent.modules.ticketing.entity.TicketType;
import com.smartevent.modules.ticketing.repository.InventoryCounterRepository;
import com.smartevent.modules.ticketing.repository.TicketSalePhaseRepository;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalePhaseLifecycleWorker {

    private final TicketSalePhaseRepository ticketSalePhaseRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;
    private final InventoryCounterRepository inventoryCounterRepository;

    @Scheduled(fixedRate = 30000)
    @Transactional
    public void processSalePhaseLifecycle() {
        Instant now = Instant.now();

        // 1. Chuyển SCHEDULED -> ACTIVE khi đến giờ mở bán và sự kiện đã PUBLISHED
        List<TicketSalePhase> scheduledPhases = ticketSalePhaseRepository
                .findByStatusAndSaleStartAtLessThanEqualAndSaleEndAtAfter(SalePhaseStatus.SCHEDULED, now, now);
        activatePhases(scheduledPhases);

        // 1b. Tự động kích hoạt DRAFT -> ACTIVE khi sự kiện đã PUBLISHED và đang trong thời gian mở bán
        List<TicketSalePhase> draftPhases = ticketSalePhaseRepository
                .findByStatusAndSaleStartAtLessThanEqualAndSaleEndAtAfter(SalePhaseStatus.DRAFT, now, now);
        activatePhases(draftPhases);

        // 2. Chuyển ACTIVE -> CLOSED khi quá hạn saleEndAt
        List<TicketSalePhase> expiredPhases = ticketSalePhaseRepository
                .findByStatusAndSaleEndAtBefore(SalePhaseStatus.ACTIVE, now);

        for (TicketSalePhase phase : expiredPhases) {
            try {
                phase.setStatus(SalePhaseStatus.CLOSED);
                ticketSalePhaseRepository.save(phase);
                log.info("SalePhaseLifecycleWorker: Đóng đợt bán hết hạn {} (ID: {}) -> CLOSED", phase.getName(), phase.getId());
            } catch (Exception e) {
                log.error("Lỗi khi đóng đợt bán {}: {}", phase.getId(), e.getMessage());
            }
        }

        // 3. Chuyển ACTIVE -> SOLD_OUT khi toàn bộ vé đã bán hết
        List<TicketSalePhase> activePhases = ticketSalePhaseRepository.findByStatus(SalePhaseStatus.ACTIVE);
        for (TicketSalePhase phase : activePhases) {
            try {
                Optional<InventoryCounter> counterOpt = inventoryCounterRepository.findBySalePhaseId(phase.getId());
                if (counterOpt.isPresent()) {
                    InventoryCounter counter = counterOpt.get();
                    if (counter.getSoldQuantity() >= counter.getTotalQuantity()) {
                        phase.setStatus(SalePhaseStatus.SOLD_OUT);
                        phase.setSoldOutAt(now);
                        ticketSalePhaseRepository.save(phase);
                        log.info("SalePhaseLifecycleWorker: Đợt bán {} (ID: {}) đã hết vé -> SOLD_OUT", phase.getName(), phase.getId());
                    }
                }
            } catch (Exception e) {
                log.error("Lỗi khi kiểm tra SOLD_OUT đợt bán {}: {}", phase.getId(), e.getMessage());
            }
        }
    }

    private void activatePhases(List<TicketSalePhase> phases) {
        if (phases == null || phases.isEmpty()) return;
        for (TicketSalePhase phase : phases) {
            try {
                Optional<TicketType> ticketTypeOpt = ticketTypeRepository.findById(phase.getTicketTypeId());
                if (ticketTypeOpt.isPresent()) {
                    Optional<Event> eventOpt = eventRepository.findById(ticketTypeOpt.get().getEventId());
                    if (eventOpt.isPresent() && eventOpt.get().getStatus() == EventStatus.PUBLISHED) {
                        phase.setStatus(SalePhaseStatus.ACTIVE);
                        ticketSalePhaseRepository.save(phase);
                        log.info("SalePhaseLifecycleWorker: Kích hoạt đợt bán {} (ID: {}) -> ACTIVE", phase.getName(), phase.getId());
                    }
                }
            } catch (Exception e) {
                log.error("Lỗi khi kích hoạt đợt bán {}: {}", phase.getId(), e.getMessage());
            }
        }
    }
}
