package com.medifind.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoice_attributes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long invoiceId;

    @Column(nullable = false)
    private String attributeName;

    @Column(columnDefinition = "TEXT")
    private String attributeValue;

    @Column(precision = 5, scale = 2)
    private BigDecimal confidence;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
