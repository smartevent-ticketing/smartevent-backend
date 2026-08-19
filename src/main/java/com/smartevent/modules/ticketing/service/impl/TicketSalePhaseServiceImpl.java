package com.smartevent.modules.ticketing.service.impl;

import com.smartevent.common.enums.EventStatus;
import com.smartevent.common.enums.SalePhaseStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventArea;
import com.smartevent.modules.event.repository.EventAreaRepository;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.ticketing.dto.request.TicketPhaseRuleRequest;
import com.smartevent.modules.ticketing.dto.request.TicketSalePhaseRequest;
import com.smartevent.modules.ticketing.dto.response.TicketPhaseRuleResponse;
import com.smartevent.modules.ticketing.dto.response.TicketSalePhaseResponse;
import com.smartevent.modules.ticketing.entity.TicketPhaseRule;
import com.smartevent.modules.ticketing.entity.TicketSalePhase;
import com.smartevent.modules.ticketing.entity.TicketType;
import com.smartevent.modules.ticketing.exception.TicketingException;
import com.smartevent.modules.ticketing.repository.TicketPhaseRuleRepository;
import com.smartevent.modules.ticketing.repository.TicketSalePhaseRepository;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import com.smartevent.modules.ticketing.service.TicketSalePhaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketSalePhaseServiceImpl implements TicketSalePhaseService {

    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;
    private final EventAreaRepository eventAreaRepository;
    private final TicketSalePhaseRepository ticketSalePhaseRepository;
    private final TicketPhaseRuleRepository ticketPhaseRuleRepository;

    @Override
    @Transactional
    public TicketSalePhaseResponse createSalePhase(UUID ticketTypeId, UUID currentUserId, boolean isAdmin, TicketSalePhaseRequest request) {
        // 1. Kiểm tra Loại vé
        TicketType ticketType = ticketTypeRepository.findById(ticketTypeId)
                .orElseThrow(() -> new TicketingException(ErrorCode.TICKET_TYPE_NOT_FOUND, "Không tìm thấy loại vé"));

        // 2. Tấm khiên 1: Xác thực sở hữu sự kiện & State Guard
        Event event = getEventAndVerifyAccess(ticketType.getEventId(), currentUserId, isAdmin);
        validateEventStateForModification(event);

        // 3. Tấm khiên 2: Time Guard
        if (!request.saleEndAt().isAfter(request.saleStartAt())) {
            throw new TicketingException(ErrorCode.SALE_PHASE_INVALID_TIME, "Thời gian kết thúc mở bán phải sau thời gian bắt đầu");
        }
        // 4. Tấm khiên 3: Capacity Guard
        EventArea area = eventAreaRepository.findById(ticketType.getEventAreaId())
                .orElseThrow(() -> new TicketingException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy khu vực/khán đài"));
        int currentConfiguredQuantity = ticketSalePhaseRepository.sumQuantityByEventAreaIdExcluding(area.getId(), null);
        int newTotalQuantity = currentConfiguredQuantity + request.quantity();
        if (newTotalQuantity > area.getCapacity()) {
            throw new TicketingException(ErrorCode.PHASE_CAPACITY_EXCEEDED,
                    String.format("Tổng số vé phát hành (%d) vượt quá sức chứa khán đài (%d)", newTotalQuantity, area.getCapacity()));
        }
        // 5. Lưu Entity
        TicketSalePhase phase = new TicketSalePhase(
                ticketTypeId,
                request.name(),
                request.price(),
                request.quantity(),
                request.saleStartAt(),
                request.saleEndAt(),
                request.maxPerOrder(),
                request.maxPerUser(),
                request.status()
        );
        TicketSalePhase saved = ticketSalePhaseRepository.save(phase);
        log.info("Tạo đợt mở bán mới: {} (ID: {}) cho loại vé {}", saved.getName(), saved.getId(), ticketTypeId);
        return TicketSalePhaseResponse.of(saved, ticketType.getName(), List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketSalePhaseResponse> getSalePhasesByTicketTypeId(UUID ticketTypeId) {
        TicketType ticketType = ticketTypeRepository.findById(ticketTypeId)
                .orElseThrow(() -> new TicketingException(
                        ErrorCode.TICKET_TYPE_NOT_FOUND, "Không tìm thấy loại vé"
                ));

        List<TicketSalePhase> phases = ticketSalePhaseRepository.findByTicketTypeId(ticketTypeId);

        return phases.stream()
                .map(phase -> {
                    List<TicketPhaseRuleResponse> rules = ticketPhaseRuleRepository.findBySalePhaseId(phase.getId())
                                    .stream().map(TicketPhaseRuleResponse::from).toList();
                    return TicketSalePhaseResponse.of(phase, ticketType.getName(), rules);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketSalePhaseResponse> getSalePhasesByEventId(UUID eventId) {

        if (!eventRepository.existsById(eventId)) {
            throw new TicketingException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sự kiện");
        }
        List<TicketType> ticketTypes = ticketTypeRepository.findByEventId(eventId);
        List<UUID> ticketTypeIds = ticketTypes.stream().map(TicketType::getId).toList();
        List<TicketSalePhase> phases = ticketSalePhaseRepository.findByTicketTypeIdIn(ticketTypeIds);
        return phases.stream()
                .map(phase -> {
                    String typeName = ticketTypes.stream()
                            .filter(tt -> tt.getId().equals(phase.getTicketTypeId()))
                            .findFirst()
                            .map(TicketType::getName)
                            .orElse("Unknown");
                    List<TicketPhaseRuleResponse> rules = ticketPhaseRuleRepository.findBySalePhaseId(phase.getId())
                            .stream().map(TicketPhaseRuleResponse::from).toList();
                    return TicketSalePhaseResponse.of(phase, typeName, rules);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TicketSalePhaseResponse getSalePhaseById(UUID id) {

        TicketSalePhase phase = ticketSalePhaseRepository.findById(id)
                .orElseThrow(() -> new TicketingException(ErrorCode.SALE_PHASE_NOT_FOUND, "Không tìm thấy đợt mở bán"));

        TicketType ticketType = ticketTypeRepository.findById(phase.getTicketTypeId()).orElse(null);
        String ticketTypeName = ticketType != null ? ticketType.getName() : "Unknown";

        List<TicketPhaseRuleResponse> rules = ticketPhaseRuleRepository.findBySalePhaseId(phase.getId())
                .stream().map(TicketPhaseRuleResponse::from).toList();

        return TicketSalePhaseResponse.of(phase, ticketTypeName, rules);
    }

    @Override
    @Transactional
    public TicketSalePhaseResponse updateSalePhase(UUID id, UUID currentUserId, boolean isAdmin, TicketSalePhaseRequest request) {

        TicketSalePhase phase = ticketSalePhaseRepository.findById(id)
                .orElseThrow(() -> new TicketingException(ErrorCode.SALE_PHASE_NOT_FOUND, "Không tìm thấy đợt mở bán"));

        TicketType ticketType = ticketTypeRepository.findById(phase.getTicketTypeId())
                .orElseThrow(() -> new TicketingException(ErrorCode.TICKET_TYPE_NOT_FOUND, "Không tìm thấy loại vé"));

        Event event = getEventAndVerifyAccess(ticketType.getEventId(), currentUserId, isAdmin);
        validateEventStateForModification(event);

        if (!request.saleEndAt().isAfter(request.saleStartAt())) {
            throw new TicketingException(ErrorCode.SALE_PHASE_INVALID_TIME, "Thời gian kết thúc mở bán phải sau thời gian bắt đầu");
        }

        EventArea area = eventAreaRepository.findById(ticketType.getEventAreaId())
                .orElseThrow(() -> new TicketingException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy khu vực/khán đài"));

        int currentConfiguredQuantity = ticketSalePhaseRepository.sumQuantityByEventAreaIdExcluding(area.getId(), id);
        int newTotalQuantity = currentConfiguredQuantity + request.quantity();
        if (newTotalQuantity > area.getCapacity()) {
            throw new TicketingException(ErrorCode.PHASE_CAPACITY_EXCEEDED,
                    String.format("Tổng số vé phát hành (%d) vượt quá sức chứa khán đài (%d)", newTotalQuantity, area.getCapacity()));
        }

        phase.setName(request.name());
        phase.setPrice(request.price());
        phase.setQuantity(request.quantity());
        phase.setSaleStartAt(request.saleStartAt());
        phase.setSaleEndAt(request.saleEndAt());
        if (request.maxPerOrder() != null) {
            phase.setMaxPerOrder(request.maxPerOrder());
        }
        phase.setMaxPerUser(request.maxPerUser());
        if (request.status() != null) {
            phase.setStatus(request.status());
        }

        TicketSalePhase updated = ticketSalePhaseRepository.save(phase);
        log.info("Cập nhật đợt mở bán: {} (ID: {})", updated.getName(), id);
        List<TicketPhaseRuleResponse> rules = ticketPhaseRuleRepository.findBySalePhaseId(phase.getId())
                .stream().map(TicketPhaseRuleResponse::from).toList();
        return TicketSalePhaseResponse.of(updated, ticketType.getName(), rules);
    }

    @Override
    @Transactional
    public TicketSalePhaseResponse updateStatus(UUID id, UUID currentUserId, boolean isAdmin, SalePhaseStatus newStatus) {

        // Kiểm tra đợt mở bán
        TicketSalePhase phase = ticketSalePhaseRepository.findById(id)
                .orElseThrow(() -> new TicketingException(ErrorCode.SALE_PHASE_NOT_FOUND, "Không tìm thấy đợt mở bán"));
        // Kiểm tra loại vé
        TicketType ticketType = ticketTypeRepository.findById(phase.getTicketTypeId())
                .orElseThrow(() -> new TicketingException(ErrorCode.TICKET_TYPE_NOT_FOUND, "Không tìm thấy loại vé"));
        // Kiểm tra quyền chỉnh sửa
        getEventAndVerifyAccess(ticketType.getEventId(), currentUserId, isAdmin);

        // State Machine validation
        validateStateTransition(phase.getStatus(), newStatus);
        phase.setStatus(newStatus);
        TicketSalePhase updated = ticketSalePhaseRepository.save(phase);
        log.info("Chuyển trạng thái đợt bán {}: {} -> {}", id, phase.getStatus(), newStatus);
        List<TicketPhaseRuleResponse> rules = ticketPhaseRuleRepository.findBySalePhaseId(phase.getId())
                .stream().map(TicketPhaseRuleResponse::from).toList();
        return TicketSalePhaseResponse.of(updated, ticketType.getName(), rules);
    }

    @Override
    @Transactional
    public void deleteSalePhase(UUID id, UUID currentUserId, boolean isAdmin) {

        // Kiểm tra đợt bán
        TicketSalePhase phase = ticketSalePhaseRepository.findById(id)
                .orElseThrow(() -> new TicketingException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đợt bán"));

        //Kiểm tra loại vé
        TicketType ticketType = ticketTypeRepository.findById(phase.getTicketTypeId())
                .orElseThrow(() -> new TicketingException(ErrorCode.TICKET_TYPE_NOT_FOUND, "Không tìm thấy loại vé"));

        // Kiểm tra trạng thái sự kiện
        Event event = getEventAndVerifyAccess(ticketType.getEventId(), currentUserId, isAdmin);
        validateEventStateForModification(event);

        if (phase.getStatus() == SalePhaseStatus.ACTIVE || phase.getStatus() == SalePhaseStatus.SOLD_OUT) {
            throw new TicketingException(ErrorCode.BUSINESS_RULE_VIOLATION, "Không thể xóa đợt bán đang hoạt động hoặc đã bán hết");
        }

        ticketSalePhaseRepository.delete(phase);
        log.warn("Đã xóa đợt mở bán: {} (ID: {})", phase.getName(), id);

    }
    @Override
    @Transactional
    public TicketPhaseRuleResponse addRule(UUID salePhaseId, UUID currentUserId, boolean isAdmin, TicketPhaseRuleRequest request) {
        TicketSalePhase phase = ticketSalePhaseRepository.findById(salePhaseId)
                .orElseThrow(() -> new TicketingException(ErrorCode.SALE_PHASE_NOT_FOUND, "Không tìm thấy đợt mở bán"));
        TicketType ticketType = ticketTypeRepository.findById(phase.getTicketTypeId())
                .orElseThrow(() -> new TicketingException(ErrorCode.TICKET_TYPE_NOT_FOUND, "Không tìm thấy loại vé"));
        Event event = getEventAndVerifyAccess(ticketType.getEventId(), currentUserId, isAdmin);
        validateEventStateForModification(event);
        if (ticketPhaseRuleRepository.existsBySalePhaseIdAndRuleType(salePhaseId, request.ruleType())) {
            throw new TicketingException(ErrorCode.RULE_ALREADY_EXISTS, "Quy tắc loại '" + request.ruleType() + "' đã tồn tại trong đợt bán này");
        }
        TicketPhaseRule rule = new TicketPhaseRule(salePhaseId, request.ruleType(), request.ruleValue());
        TicketPhaseRule savedRule = ticketPhaseRuleRepository.save(rule);
        log.info("Thêm quy tắc mới ({}) cho đợt bán {}", savedRule.getRuleType(), salePhaseId);
        return TicketPhaseRuleResponse.from(savedRule);
    }
    @Override
    @Transactional
    public void deleteRule(UUID ruleId, UUID currentUserId, boolean isAdmin) {
        TicketPhaseRule rule = ticketPhaseRuleRepository.findById(ruleId)
                .orElseThrow(() -> new TicketingException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy quy tắc"));
        TicketSalePhase phase = ticketSalePhaseRepository.findById(rule.getSalePhaseId())
                .orElseThrow(() -> new TicketingException(ErrorCode.SALE_PHASE_NOT_FOUND, "Không tìm thấy đợt mở bán"));
        TicketType ticketType = ticketTypeRepository.findById(phase.getTicketTypeId())
                .orElseThrow(() -> new TicketingException(ErrorCode.TICKET_TYPE_NOT_FOUND, "Không tìm thấy loại vé"));
        Event event = getEventAndVerifyAccess(ticketType.getEventId(), currentUserId, isAdmin);
        validateEventStateForModification(event);
        ticketPhaseRuleRepository.delete(rule);
        log.warn("Đã xóa quy tắc: {} (ID: {})", rule.getRuleType(), ruleId);
    }




    private Event getEventAndVerifyAccess(UUID eventId, UUID currentUserId, boolean isAdmin) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new TicketingException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sự kiện"));
        if (!isAdmin && !event.getOrganizerId().equals(currentUserId)) {
            throw new TicketingException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền quản lý đợt mở bán của sự kiện này");
        }
        return event;
    }
    private void validateEventStateForModification(Event event) {
        if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.PENDING_APPROVAL) {
            throw new TicketingException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Chỉ có thể chỉnh sửa đợt mở bán khi sự kiện ở trạng thái Nháp hoặc Chờ duyệt");
        }
    }


    private void validateStateTransition(SalePhaseStatus currentStatus, SalePhaseStatus newStatus) {
        if (currentStatus == newStatus) return;
        boolean isValid = switch (currentStatus) {
            case DRAFT -> newStatus == SalePhaseStatus.SCHEDULED || newStatus == SalePhaseStatus.ACTIVE;
            case SCHEDULED -> newStatus == SalePhaseStatus.ACTIVE || newStatus == SalePhaseStatus.CLOSED;
            case ACTIVE -> newStatus == SalePhaseStatus.PAUSED || newStatus == SalePhaseStatus.CLOSED || newStatus == SalePhaseStatus.SOLD_OUT;
            case PAUSED -> newStatus == SalePhaseStatus.ACTIVE || newStatus == SalePhaseStatus.CLOSED;
            case CLOSED, SOLD_OUT -> false; // Đã đóng hoặc hết vé thì không thể tự ý chuyển lại trạng thái khác
        };
        if (!isValid) {
            throw new TicketingException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    String.format("Không thể chuyển trạng thái đợt bán từ %s sang %s", currentStatus, newStatus));
        }
    }
}
