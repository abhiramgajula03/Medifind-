package com.medifind.repository;

import com.medifind.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByShopId(Long shopId);
    List<Invoice> findByShopIdAndAgencyId(Long shopId, Long agencyId);
    Optional<Invoice> findByInvoiceNo(String invoiceNo);
    Optional<Invoice> findTopByShopIdAndAgencyIdAndTotalAmountOrderByCreatedAtDesc(Long shopId, Long agencyId, BigDecimal totalAmount);
}
