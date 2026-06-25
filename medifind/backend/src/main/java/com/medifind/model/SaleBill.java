package com.medifind.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
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
    private String pincode;
    private String customerName;
    @Column(nullable = false)
    private String medicineName;
    private Integer quantity = 1;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;
    @Column(precision = 12, scale = 2)
    private BigDecimal sgst;
    @Column(precision = 12, scale = 2)
    private BigDecimal cgst;
    @Column(precision = 12, scale = 2)
    private BigDecimal serviceTax;
    @Column(precision = 12, scale = 2)
    private BigDecimal total;
    private LocalDateTime billedAt = LocalDateTime.now();
}
