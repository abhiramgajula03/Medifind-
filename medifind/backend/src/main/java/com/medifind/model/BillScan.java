package com.medifind.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bill_scans")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillScan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long shopId;

    @Column(name = "original_file_name")
    private String originalFileName;

    @Column(columnDefinition = "LONGTEXT")
    private String imageUrl;

    @Column(name = "cropped_image_url", columnDefinition = "LONGTEXT")
    private String croppedImageUrl;

    @Column(name = "enhanced_image_url", columnDefinition = "LONGTEXT")
    private String enhancedImageUrl;

    @Column(columnDefinition = "LONGTEXT")
    private String extractedText;

    @Column(columnDefinition = "JSON")
    private String parsedItems;

    @Column(name = "ocr_document_json", columnDefinition = "LONGTEXT")
    private String ocrDocumentJson;

    @Transient
    private Invoice extractedInvoice;

    @Enumerated(EnumType.STRING)
    private ProcessingStatus processingStatus = ProcessingStatus.PROCESSING;

    @Column(columnDefinition = "TEXT")
    private String processingError;

    @Column(columnDefinition = "JSON")
    private String detectionBounds;

    @Column(columnDefinition = "JSON")
    private String processingSteps;

    @Column(name = "extraction_confidence", precision = 5, scale = 2)
    private java.math.BigDecimal extractionConfidence;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum ProcessingStatus {
        PROCESSING, COMPLETED, FAILED
    }
}
