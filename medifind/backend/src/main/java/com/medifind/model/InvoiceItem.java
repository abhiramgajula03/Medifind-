package com.medifind.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoice_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long invoiceId;

    @Column(nullable = false)
    private Long shopId;

    @Column(name = "s_no")
    private Integer sNo;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_code")
    private String productCode;

    @Column(name = "hsn_code")
    private String hsnCode;

    @Column(name = "manufacturer")
    private String manufacturer;

    @Column(name = "pack")
    private String pack;

    private Integer quantity;

    private String unit;

    @Column(name = "batch_no")
    private String batchNo;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(precision = 12, scale = 2)
    private BigDecimal mrp;

    @Column(precision = 12, scale = 2)
    private BigDecimal rate;

    @Column(precision = 12, scale = 2)
    private BigDecimal discount;

    @Column(precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(precision = 5, scale = 2)
    private BigDecimal gstPercent;

    @Column(precision = 5, scale = 2)
    private BigDecimal cgstRate;

    @Column(precision = 12, scale = 2)
    private BigDecimal cgstValue = BigDecimal.ZERO;

    @Column(precision = 5, scale = 2)
    private BigDecimal sgstRate;

    @Column(precision = 12, scale = 2)
    private BigDecimal sgstValue = BigDecimal.ZERO;

    @Column(precision = 5, scale = 2)
    private BigDecimal igstRate;

    @Column(precision = 12, scale = 2)
    private BigDecimal igstValue = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal taxableValue;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
