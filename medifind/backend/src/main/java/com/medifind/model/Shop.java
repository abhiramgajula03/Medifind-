package com.medifind.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

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

    private String ownerName;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lng;

    @Column(name = "is_open")
    private Boolean isOpen = false;

    @Column(name = "is_24hr")
    private Boolean is24hr = false;

    private LocalTime openTime;
    private LocalTime closeTime;

    private String licenseNumber;
    private String pincode;

    @Column(columnDefinition = "BIGINT DEFAULT 0")
    private Long views = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @JsonIgnore
    private User createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // Utility: calculate distance in km from given coordinates (Haversine formula)
    @Transient
    public double distanceTo(double userLat, double userLng) {
        final int R = 6371;
        double latDist = Math.toRadians(userLat - this.lat);
        double lngDist = Math.toRadians(userLng - this.lng);
        double a = Math.sin(latDist / 2) * Math.sin(latDist / 2)
                + Math.cos(Math.toRadians(this.lat)) * Math.cos(Math.toRadians(userLat))
                * Math.sin(lngDist / 2) * Math.sin(lngDist / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
