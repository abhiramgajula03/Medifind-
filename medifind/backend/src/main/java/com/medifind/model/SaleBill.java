package com.medifind.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sale_bills")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long shopId;

    @Column(nullable = false)
    private String shopName;

    @Column(name = "pincode")
    private String pincode;

    @Column(name = "customer_name")
    private String customerName;

    @Column(nullable = false)
    private String medicineName;

    @Column(name = "inventory_id") private Long inventoryId;
    @Column(name = "selling_unit") private String sellingUnit;
    @Column(name = "pack_size") private Integer packSize;
    private String pack;
    @Column(name = "batch_no") private String batchNo;
    @Column(name = "expiry_date") private LocalDate expiryDate;
    private String manufacturer;
    @Column(name = "hsn_code") private String hsnCode;
    @Column(name = "receipt_no") private String receiptNo;

    private Integer quantity = 1;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(precision = 12, scale = 2)
    private BigDecimal sgst = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal cgst = BigDecimal.ZERO;

    @Column(name = "service_tax", precision = 12, scale = 2)
    private BigDecimal serviceTax = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "cgst_rate", precision = 5, scale = 2) private BigDecimal cgstRate;
    @Column(name = "sgst_rate", precision = 5, scale = 2) private BigDecimal sgstRate;

    @CreationTimestamp
    @Column(name = "billed_at")
    private LocalDateTime billedAt;
}
