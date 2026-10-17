package com.medifind.controller;

import com.medifind.model.Invoice;
import com.medifind.service.BillScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class InvoiceController {

    private final BillScanService billScanService;

    @GetMapping("/shop/{shopId}")
    @PreAuthorize("hasRole('SHOPKEEPER')")
    public ResponseEntity<List<Invoice>> getInvoices(@PathVariable Long shopId) {
        return ResponseEntity.ok(billScanService.getInvoicesByShop(shopId));
    }

    @GetMapping("/{invoiceId}")
    @PreAuthorize("hasRole('SHOPKEEPER')")
    public ResponseEntity<Invoice> getInvoice(@PathVariable Long invoiceId) {
        return ResponseEntity.ok(billScanService.getInvoiceById(invoiceId));
    }

    @PatchMapping("/{invoiceId}/verify")
    @PreAuthorize("hasRole('SHOPKEEPER')")
    public ResponseEntity<?> verifyInvoice(@PathVariable Long invoiceId, @RequestBody Map<String, Object> verifiedData) {
        billScanService.verifyInvoice(invoiceId, verifiedData);
        return ResponseEntity.ok(Map.of("message", "Invoice verified successfully", "invoiceId", invoiceId));
    }
}
