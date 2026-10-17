package com.medifind.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long shopId;

    @Column(nullable = false)
    private Long agencyId;

    @Column(nullable = false)
    private String agencyName;

    @Column(nullable = false)
    private String invoiceType;

    @Column(nullable = false, unique = true)
    private String invoiceNo;

    @Column(nullable = false)
    private LocalDate invoiceDate;

    private LocalDate dueDate;

    @Column(name = "order_no")
    private String orderNo;

    @Column(name = "order_date")
    private LocalDate orderDate;

    @Column(name = "order_due_date")
    private LocalDate orderDueDate;

    private Integer cases;

    @Column(name = "lr_no")
    private String lrNo;

    @Column(name = "lr_date")
    private LocalDate lrDate;

    @Column(name = "eway_bill_no")
    private String ewayBillNo;

    @Column(name = "vehicle_no")
    private String vehicleNo;

    private String transporter;

    @Column(name = "total_items")
    private Integer totalItems;

    @Column(precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "total_taxable_amount", precision = 12, scale = 2)
    private BigDecimal totalTaxableAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "gross_amount", precision = 12, scale = 2)
    private BigDecimal grossAmount = BigDecimal.ZERO;

    @Column(name = "net_amount", precision = 12, scale = 2)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Column(name = "other_charges", precision = 12, scale = 2)
    private BigDecimal otherCharges = BigDecimal.ZERO;

    @Column(name = "supplier_name")
    private String supplierName;

    @Column(name = "dealer_name")
    private String dealerName;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "gst_number")
    private String gstNumber;

    @Column(name = "pan_tin")
    private String panTin;

    @Column(name = "payment_mode")
    private String paymentMode;

    @Column(columnDefinition = "TEXT")
    private String bankInfo;

    @Column(columnDefinition = "JSON")
    private String supplierJson;

    @Column(columnDefinition = "JSON")
    private String partyJson;

    @Column(columnDefinition = "LONGTEXT")
    private String billPhoto;

    @Column(columnDefinition = "LONGTEXT")
    private String rawText;

    @Column(name = "is_verified")
    private Boolean isVerified = false;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
