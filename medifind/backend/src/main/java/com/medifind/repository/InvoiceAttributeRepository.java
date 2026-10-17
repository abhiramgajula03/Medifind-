package com.medifind.repository;

import com.medifind.model.InvoiceAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceAttributeRepository extends JpaRepository<InvoiceAttribute, Long> {
    List<InvoiceAttribute> findByInvoiceId(Long invoiceId);
    void deleteByInvoiceId(Long invoiceId);
}
