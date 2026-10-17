package com.medifind.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "shops")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "owner_name")
    private String ownerName;

    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "pincode")
    private String pincode;

    @Column(name = "license_number")
    private String licenseNumber;

    @Column(name = "license_photo", columnDefinition = "LONGTEXT")
    private String licensePhoto;

    private Double lat;

    private Double lng;

    @Column(name = "is_open")
    private Boolean isOpen = false;

    @Column(name = "is_24hr")
    private Boolean is24hr = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @JsonIgnore
    private User createdBy;
}
