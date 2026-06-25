package com.medifind.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shortage_list")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortageList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Column(nullable = false)
    private String medicineName;

    // Customer info (can be walk-in / offline customer)
    private String customerName;
    private String customerPhone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private User customer; // null if offline/walk-in

    @Column(nullable = false)
    private Integer quantityNeeded = 1;

    @Column(precision = 10, scale = 2)
    private BigDecimal advancePaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus = PaymentStatus.NONE;

    private String paymentTransactionId;

    @Enumerated(EnumType.STRING)
    private ShortageStatus status = ShortageStatus.PENDING;

    private String notes;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum PaymentStatus {
        NONE, PENDING, PAID, REFUNDED
    }

    public enum ShortageStatus {
        PENDING,   // Shopkeeper noted, to order
        ORDERED,   // Sent to distributor
        ARRIVED,   // Medicine arrived at shop
        DELIVERED, // Customer collected
        CANCELLED  // Not available, advance refunded
    }
}
