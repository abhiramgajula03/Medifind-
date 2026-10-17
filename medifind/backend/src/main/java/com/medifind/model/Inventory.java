package com.medifind.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long shopId;

    @Column(nullable = false)
    private String medicineName;

    private Integer quantity = 0;

    @Column(columnDefinition = "VARCHAR(50) DEFAULT 'strips'")
    private String unit = "strips";

    @Column(precision = 12, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    /** Canonical on-hand quantity. Recognised tablet/capsule packs use individual units. */
    @Column(name = "quantity_in_base_units")
    private Integer quantityInBaseUnits;

    @Column(name = "pack_size")
    private Integer packSize = 1;

    @Column(name = "base_unit") private String baseUnit;
    private String pack;
    @Column(name = "batch_no") private String batchNo;
    @Column(name = "expiry_date") private LocalDate expiryDate;
    private String manufacturer;
    @Column(name = "hsn_code") private String hsnCode;
    @Column(name = "cgst_rate", precision = 5, scale = 2) private BigDecimal cgstRate;
    @Column(name = "sgst_rate", precision = 5, scale = 2) private BigDecimal sgstRate;
    @Column(name = "source_agency") private String sourceAgency;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
