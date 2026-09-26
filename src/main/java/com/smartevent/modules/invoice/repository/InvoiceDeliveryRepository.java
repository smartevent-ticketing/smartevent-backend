package com.smartevent.modules.invoice.repository;

import com.smartevent.modules.invoice.entity.InvoiceDelivery;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceDeliveryRepository extends JpaRepository<InvoiceDelivery, UUID> {

    List<InvoiceDelivery> findByInvoiceId(UUID invoiceId);

    List<InvoiceDelivery> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from InvoiceDelivery d where d.id = :id")
    Optional<InvoiceDelivery> findByIdForUpdate(@Param("id") UUID id);
}
