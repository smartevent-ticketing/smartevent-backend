package com.smartevent.modules.ticketing.repository;

import com.smartevent.modules.ticketing.entity.TicketPhaseRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketPhaseRuleRepository extends JpaRepository<TicketPhaseRule, UUID> {

    List<TicketPhaseRule> findBySalePhaseId(UUID salePhaseId);

    boolean existsBySalePhaseIdAndRuleType(UUID salePhaseId, String ruleType);
}