package com.medifind.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "attribute_registry")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttributeRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String attributeName;

    @Column(nullable = false)
    private String canonicalName;

    @Column(name = "data_type")
    private String dataType = "TEXT";

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
