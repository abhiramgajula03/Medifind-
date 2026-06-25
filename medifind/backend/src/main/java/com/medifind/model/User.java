package com.medifind.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String phone;

    // Shopkeeper registration and licence verification
    private String shopName;
    private String shopAddress;
    private String pincode;
    private String licenseNumber;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String licensePhoto;

    @Enumerated(EnumType.STRING)
    private ApprovalStatus approvalStatus = ApprovalStatus.NOT_REQUIRED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.CUSTOMER;

    // Saved home/work location
    private Double savedLat;
    private Double savedLng;
    private String savedAddress;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Role {
        CUSTOMER, ADMIN, SHOPKEEPER
    }

    public enum ApprovalStatus {
        NOT_REQUIRED, PENDING, APPROVED, REJECTED
    }
}
