package com.smartevent.modules.ticketing.support;
import java.time.Clock;
import java.util.UUID;
import com.smartevent.common.enums.SalePhaseStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.ticketing.entity.TicketSalePhase;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import com.smartevent.modules.ticketing.repository.TicketSalePhaseRepository;
import com.smartevent.modules.ticketing.exception.TicketingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class ChildEventReadAccess {
    private final com.smartevent.modules.event.service.EventReadAccess eventAccess;
    private final TicketTypeRepository types;
    private final TicketSalePhaseRepository phases;
    private final Clock clock;
    public boolean requireEvent(UUID id) { return eventAccess.requireEvent(id); }
    public boolean requireArea(UUID id) { return eventAccess.requireArea(id); }
    public boolean requireType(UUID id) {
        var type = types.findById(id).orElseThrow(this::denied);
        boolean privileged = requireEvent(type.getEventId());
        if (!privileged && type.getStatus() != com.smartevent.common.enums.TicketTypeStatus.ACTIVE) throw denied();
        return privileged;
    }
    public boolean requirePhase(UUID id) {
        var phase = phases.findById(id).orElseThrow(this::denied);
        boolean privileged = requireType(phase.getTicketTypeId());
        if (!privileged && !publicPhase(phase)) throw denied();
        return privileged;
    }
    public boolean publicPhase(TicketSalePhase phase) {
        var now = clock.instant();
        return phase.getStatus() == SalePhaseStatus.ACTIVE && !now.isBefore(phase.getSaleStartAt())
            && !now.isAfter(phase.getSaleEndAt()) && phase.getSoldOutAt() == null;
    }
    public java.util.Set<UUID> publicTypePhaseIds(UUID typeId) {
        var type = types.findById(typeId).orElseThrow(this::denied);
        return publicPhaseIds(type.getEventId());
    }
    public java.util.Set<UUID> publicPhaseIds(UUID eventId) {
        var typeIds = types.findByEventId(eventId).stream()
            .filter(t -> t.getStatus() == com.smartevent.common.enums.TicketTypeStatus.ACTIVE)
            .map(t -> t.getId()).toList();
        if (typeIds.isEmpty()) return java.util.Set.of();
        return phases.findByTicketTypeIdInOrderBySaleStartAtAscIdAsc(typeIds).stream()
            .filter(this::publicPhase).map(p -> p.getId()).collect(java.util.stream.Collectors.toSet());
    }
    private TicketingException denied() {
        return new TicketingException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài nguyên công khai");
    }
}
