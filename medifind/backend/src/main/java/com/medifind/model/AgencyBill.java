package com.medifind.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "agency_bills")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgencyBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long agencyId;

    @Column(nullable = false)
    private String agencyName;

    @Column(nullable = false)
    private Long shopId;

    /** Links the supplier bill to the canonical invoice and its invoice_items. */
    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(nullable = false)
    private String shopName;

    @Column(name = "bill_date")
    private LocalDateTime billDate = LocalDateTime.now();

    @Column(precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(columnDefinition = "LONGTEXT")
    private String billPhoto;

    @Column(columnDefinition = "JSON")
    private String itemsJson;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum PaymentStatus {
        PENDING, PAID, CANCELLED
    }
}
