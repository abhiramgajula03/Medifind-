package com.medifind.controller;

import com.medifind.model.SaleBill;
import com.medifind.service.BillingService;
import com.medifind.service.ShopAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class BillingController {

    private final BillingService billingService;
    private final ShopAccessService shopAccessService;

    @PostMapping("/shop/{shopId}")
    @PreAuthorize("hasRole('SHOPKEEPER')")
    public ResponseEntity<?> createBill(@PathVariable Long shopId, @RequestBody Map<String, Object> data, Authentication authentication) {
        shopAccessService.requireOwnedShop(shopId, authentication);
        SaleBill bill = billingService.createBill(shopId, data);
        return ResponseEntity.ok(bill);
    }

    @PostMapping("/shop/{shopId}/receipt")
    @PreAuthorize("hasRole('SHOPKEEPER')")
    public ResponseEntity<?> createReceipt(@PathVariable Long shopId, @RequestBody Map<String, Object> data, Authentication authentication) {
        shopAccessService.requireOwnedShop(shopId, authentication);
        var lines = billingService.createReceipt(shopId, data);
        return ResponseEntity.ok(Map.of("receiptNo", lines.get(0).getReceiptNo(), "lines", lines));
    }

    @GetMapping("/shop/{shopId}/daily")
    @PreAuthorize("hasRole('SHOPKEEPER')")
    public ResponseEntity<Map<String, Object>> getDailyBills(@PathVariable Long shopId, @RequestParam(required = false) String date, Authentication authentication) {
        shopAccessService.requireOwnedShop(shopId, authentication);
        LocalDate d = date != null ? LocalDate.parse(date) : LocalDate.now();
        return ResponseEntity.ok(billingService.getDailyBills(shopId, d));
    }
}
