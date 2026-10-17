package com.medifind.controller;

import com.medifind.model.AgencyBill;
import com.medifind.model.BillScan;
import com.medifind.service.BillScanService;
import com.medifind.service.InvoiceExtractionService;
import com.medifind.service.DocTrOcrClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bills")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class BillScanController {

    private final BillScanService billScanService;
    private final InvoiceExtractionService invoiceExtractionService;
    private final DocTrOcrClient docTrOcrClient;

    @PostMapping("/ocr/invoice")
    @PreAuthorize("hasAnyRole('SHOPKEEPER','ADMIN')")
    public ResponseEntity<?> ocrInvoice(@RequestParam MultipartFile file,
                                        @RequestParam(required = false) MultipartFile originalImage,
                                        @RequestParam(required = false) MultipartFile rotatedImage,
                                        @RequestParam(required = false) String clientImageDiagnostics) {
        if (file.isEmpty() || file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            return ResponseEntity.badRequest().body("Please select a bill image.");
        }
        try { return ResponseEntity.ok(docTrOcrClient.extractFinalInvoice(file, originalImage, rotatedImage, clientImageDiagnostics)); }
        catch (Exception error) {
            Throwable cause = error;
            while (cause.getCause() != null) cause = cause.getCause();
            return ResponseEntity.internalServerError().body("OCR failed: " + cause.getMessage());
        }
    }

    @PostMapping("/ocr/invoice/persist")
    @PreAuthorize("hasAnyRole('SHOPKEEPER','ADMIN')")
    public ResponseEntity<?> persistOcrInvoice(@RequestParam Long shopId,
                                                @RequestParam Long agencyId,
                                                @RequestParam(required = false) String agencyName,
                                                @RequestBody Map<String, Object> document) {
        try {
            var invoice = billScanService.persistStructuredInvoice(shopId, agencyId, agencyName, document);
            return ResponseEntity.ok(Map.of("invoiceId", invoice.getId(), "message", "Invoice and inventory saved."));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(error.getMessage());
        } catch (Exception error) {
            return ResponseEntity.internalServerError().body("Could not save OCR invoice: " + error.getMessage());
        }
    }

    @PostMapping("/scan")
    @PreAuthorize("hasAnyRole('SHOPKEEPER','ADMIN')")
    public ResponseEntity<?> scanBill(@RequestParam Long shopId,
                                       @RequestParam(required = false) MultipartFile file,
                                       @RequestParam(required = false) Long agencyId,
                                       @RequestParam(required = false) String agencyName) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body("Please select a bill image.");
            }
            BillScan scan = billScanService.processBillImage(shopId, file, agencyId, agencyName);
            return ResponseEntity.ok(scan);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Scan failed: " + e.getMessage());
        }
    }

    @PostMapping("/scan/process")
    @PreAuthorize("hasAnyRole('SHOPKEEPER','ADMIN')")
    public ResponseEntity<?> processScan(@RequestParam Long shopId,
                                          @RequestParam(required = false) MultipartFile file,
                                          @RequestParam(required = false) Long agencyId,
                                          @RequestParam(required = false) String agencyName) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body("Please select a bill image.");
            }
            BillScan scan = billScanService.processBillImage(shopId, file, agencyId, agencyName);
            return ResponseEntity.ok(Map.of(
                    "scan", scan,
                    "status", scan.getProcessingStatus() != null ? scan.getProcessingStatus().name() : "UNKNOWN"
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Scan failed: " + e.getMessage());
        }
    }

    @GetMapping("/scans/shop/{shopId}")
    @PreAuthorize("hasAnyRole('SHOPKEEPER','ADMIN')")
    public ResponseEntity<List<BillScan>> getScans(@PathVariable Long shopId) {
        return ResponseEntity.ok(billScanService.getScansByShop(shopId));
    }

    @GetMapping("/agency/shop/{shopId}")
    @PreAuthorize("hasAnyRole('SHOPKEEPER','ADMIN')")
    public ResponseEntity<List<AgencyBill>> getAgencyBills(@PathVariable Long shopId) {
        return ResponseEntity.ok(billScanService.getAgencyBillsByShop(shopId));
    }
}
