package com.smartevent.modules.ticketing.service.impl;

import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.ticketing.dto.response.InventoryCounterResponse;
import com.smartevent.modules.ticketing.entity.InventoryCounter;
import com.smartevent.modules.ticketing.exception.TicketingException;
import com.smartevent.modules.ticketing.repository.InventoryCounterRepository;
import com.smartevent.modules.ticketing.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryCounterRepository inventoryCounterRepository;

    @Override
    @Transactional
    public void initCounter(UUID eventId, UUID eventAreaId, UUID ticketTypeId, UUID salePhaseId, int totalQuantity) {
        if (inventoryCounterRepository.existsBySalePhaseId(salePhaseId)) {
            log.warn("InventoryCounter đã tồn tại cho đợt bán: {}", salePhaseId);
            return;
        }

        InventoryCounter counter = new InventoryCounter(eventId, eventAreaId, ticketTypeId, salePhaseId, totalQuantity);
        inventoryCounterRepository.save(counter);
        log.info("Đã khởi tạo InventoryCounter cho đợt bán {} với tổng số vé {}", salePhaseId, totalQuantity);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryCounterResponse getCounterBySalePhaseId(UUID salePhaseId) {
        InventoryCounter counter = inventoryCounterRepository.findBySalePhaseId(salePhaseId)
                .orElseThrow(() -> new TicketingException(ErrorCode.INVENTORY_COUNTER_NOT_FOUND, "Không tìm thấy bộ đếm tồn kho"));
        return InventoryCounterResponse.from(counter);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryCounterResponse> getCountersByEventId(UUID eventId) {
        return inventoryCounterRepository.findByEventId(eventId)
                .stream()
                .map(InventoryCounterResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public void holdInventory(UUID salePhaseId, int quantity) {
        if (quantity <= 0) {
            throw new TicketingException(ErrorCode.VALIDATION_ERROR, "Số lượng giữ vé phải lớn hơn 0");
        }

        int updatedRows = inventoryCounterRepository.atomicHoldQuantity(salePhaseId, quantity, Instant.now());
        if (updatedRows == 0) {
            log.warn("Không đủ số lượng vé tồn kho để giữ cho đợt bán {}", salePhaseId);
            throw new TicketingException(ErrorCode.INVENTORY_NOT_ENOUGH, "Số lượng vé khả dụng không đủ");
        }

        log.info("Đã giữ thành công {} vé cho đợt bán {}", quantity, salePhaseId);
    }

    @Override
    @Transactional
    public void releaseHeldInventory(UUID salePhaseId, int quantity) {
        if (quantity <= 0) return;

        int updatedRows = inventoryCounterRepository.atomicReleaseHeldQuantity(salePhaseId, quantity, Instant.now());
        if (updatedRows == 0) {
            log.warn("Thất bại khi nhả vé tạm giữ: salePhaseId={}, quantity={}", salePhaseId, quantity);
            throw new TicketingException(ErrorCode.BUSINESS_RULE_VIOLATION, "Số lượng vé nhả vượt quá số vé đang giữ");
        }

        log.info("Đã nhả {} vé tạm giữ của đợt bán {}", quantity, salePhaseId);
    }

    @Override
    @Transactional
    public void confirmPurchase(UUID salePhaseId, int quantity) {
        if (quantity <= 0) return;

        int updatedRows = inventoryCounterRepository.atomicConfirmPurchase(salePhaseId, quantity, Instant.now());
        if (updatedRows == 0) {
            log.error("Lỗi xác nhận thanh toán vé: không đủ held_quantity để chuyển sang sold_quantity cho phase {}", salePhaseId);
            throw new TicketingException(ErrorCode.BUSINESS_RULE_VIOLATION, "Không thể xác nhận bán vé do số lượng giữ không khớp");
        }

        log.info("Xác nhận thanh toán thành công {} vé cho đợt bán {}", quantity, salePhaseId);
    }

    @Override
    @Transactional
    public void processRefund(UUID salePhaseId, int quantity) {
        if (quantity <= 0) return;

        int updatedRows = inventoryCounterRepository.atomicProcessRefund(salePhaseId, quantity, Instant.now());
        if (updatedRows == 0) {
            log.error("Lỗi hoàn tiền vé: không đủ sold_quantity để hoàn cho phase {}", salePhaseId);
            throw new TicketingException(ErrorCode.BUSINESS_RULE_VIOLATION, "Không thể hoàn vé do số lượng đã bán không đủ");
        }

        log.info("Hoàn vé thành công {} vé cho đợt bán {}", quantity, salePhaseId);
    }

    @Override
    @Transactional
    public void updateTotalQuantity(UUID salePhaseId, int newTotalQuantity) {
        int updatedRows = inventoryCounterRepository.atomicUpdateTotalQuantity(salePhaseId, newTotalQuantity, Instant.now());
        if (updatedRows == 0) {
            throw new TicketingException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Không thể giảm tổng số vé xuống thấp hơn số vé đang giữ và đã bán");
        }
        log.info("Đã cập nhật tổng số vé của đợt bán {} thành {}", salePhaseId, newTotalQuantity);
    }
}