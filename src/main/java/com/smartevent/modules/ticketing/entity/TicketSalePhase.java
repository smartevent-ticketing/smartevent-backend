package com.smartevent.modules.ticketing.entity;

import com.smartevent.common.entity.BaseEntity;
import com.smartevent.common.enums.SalePhaseStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_sale_phases")
@Getter
@Setter
@NoArgsConstructor
public class TicketSalePhase extends BaseEntity {

    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "price", nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "sale_start_at", nullable = false)
    private Instant saleStartAt;

    @Column(name = "sale_end_at", nullable = false)
    private Instant saleEndAt;

    @Column(name = "sold_out_at")
    private Instant soldOutAt;

    @Column(name = "max_per_order", nullable = false)
    private Integer maxPerOrder = 4;

    @Column(name = "max_per_user")
    private Integer maxPerUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SalePhaseStatus status = SalePhaseStatus.DRAFT;

    public TicketSalePhase(UUID ticketTypeId, String name, BigDecimal price, Integer quantity,
                           Instant saleStartAt, Instant saleEndAt, Integer maxPerOrder,
                           Integer maxPerUser, SalePhaseStatus status) {
        this.ticketTypeId = ticketTypeId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.saleStartAt = saleStartAt;
        this.saleEndAt = saleEndAt;
        this.maxPerOrder = maxPerOrder != null ? maxPerOrder : 4;
        this.maxPerUser = maxPerUser;
        this.status = status != null ? status : SalePhaseStatus.DRAFT;
    }
}