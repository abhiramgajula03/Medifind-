package com.medifind.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    private NotificationType type = NotificationType.SHOP_UPDATE;

    @Column(nullable = false)
    private Boolean isRead = false;

    private Long relatedId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum NotificationType {
        SHOP_UPDATE,
        MEDICINE_AVAILABLE,
        SHORTAGE_UPDATE,
        AGENCY_UPDATE,
        SYSTEM
    }
}
