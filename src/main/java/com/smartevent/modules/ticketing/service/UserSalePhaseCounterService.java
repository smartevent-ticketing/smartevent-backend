package com.smartevent.modules.ticketing.service;

import com.smartevent.modules.ticketing.dto.response.UserSalePhaseCounterResponse;

import java.util.UUID;

public interface UserSalePhaseCounterService {

    UserSalePhaseCounterResponse getUserCounter(UUID userId, UUID salePhaseId);

    void holdUserTickets(UUID userId, UUID salePhaseId, int quantity, Integer maxPerUser);

    void releaseUserHeldTickets(UUID userId, UUID salePhaseId, int quantity);

    void confirmUserPurchase(UUID userId, UUID salePhaseId, int quantity);

    void processUserRefund(UUID userId, UUID salePhaseId, int quantity);
}