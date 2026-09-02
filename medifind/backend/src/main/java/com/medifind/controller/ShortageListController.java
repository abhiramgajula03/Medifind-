package com.medifind.controller;

import com.medifind.model.ShortageList;
import com.medifind.model.ShortageList.ShortageStatus;
import com.medifind.service.ShortageListService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shortage")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class ShortageListController {

    private final ShortageListService shortageListService;

    // SHOPKEEPER: Add offline/walk-in customer to shortage list
    // POST /api/shortage/shop/{shopId}/offline
    @PostMapping("/shop/{shopId}/offline")
    @PreAuthorize("hasRole('SHOPKEEPER') or hasRole('ADMIN')")
    public ResponseEntity<ShortageList> addOfflineEntry(
            @PathVariable Long shopId,
            @RequestBody Map<String, Object> body) {

        return ResponseEntity.ok(shortageListService.addShortageEntry(
                shopId,
                (String) body.get("medicineName"),
                (String) body.get("customerName"),
                (String) body.get("customerPhone"),
                (Integer) body.get("quantity"),
                (String) body.get("notes")
        ));
    }

    // CUSTOMER: Request unavailable medicine online (after Razorpay payment)
    // POST /api/shortage/shop/{shopId}/online
    @PostMapping("/shop/{shopId}/online")
    public ResponseEntity<ShortageList> addOnlineRequest(
            @PathVariable Long shopId,
            @RequestBody Map<String, Object> body) {

        BigDecimal advance = new BigDecimal(body.get("advancePaid").toString());
        return ResponseEntity.ok(shortageListService.requestMedicineOnline(
                shopId,
                body.get("customerId") != null ? Long.valueOf(body.get("customerId").toString()) : null,
                (String) body.get("customerName"),
                (String) body.get("customerPhone"),
                (String) body.get("medicineName"),
                (Integer) body.get("quantity"),
                advance,
                (String) body.get("transactionId")
        ));
    }

    // SHOPKEEPER: View their shop's shortage list
    // GET /api/shortage/shop/{shopId}
    @GetMapping("/shop/{shopId}")
    @PreAuthorize("hasRole('SHOPKEEPER') or hasRole('ADMIN')")
    public ResponseEntity<List<ShortageList>> getShortageList(@PathVariable Long shopId) {
        return ResponseEntity.ok(shortageListService.getShortageList(shopId));
    }

    // SHOPKEEPER: View only pending entries
    // GET /api/shortage/shop/{shopId}/pending
    @GetMapping("/shop/{shopId}/pending")
    @PreAuthorize("hasRole('SHOPKEEPER') or hasRole('ADMIN')")
    public ResponseEntity<List<ShortageList>> getPendingList(@PathVariable Long shopId) {
        return ResponseEntity.ok(shortageListService.getPendingShortages(shopId));
    }

    // SHOPKEEPER: Update status of a shortage entry
    // PATCH /api/shortage/{entryId}/status?status=ORDERED
    @PatchMapping("/{entryId}/status")
    @PreAuthorize("hasRole('SHOPKEEPER') or hasRole('ADMIN')")
    public ResponseEntity<ShortageList> updateStatus(
            @PathVariable Long entryId,
            @RequestParam ShortageStatus status) {
        return ResponseEntity.ok(shortageListService.updateStatus(entryId, status));
    }

    // CUSTOMER: View their own medicine requests
    // GET /api/shortage/customer/{customerId}
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<ShortageList>> getCustomerRequests(@PathVariable Long customerId) {
        return ResponseEntity.ok(shortageListService.getCustomerRequests(customerId));
    }

    // SHOPKEEPER: Export shortage list as CSV to send to distributor
    // GET /api/shortage/shop/{shopId}/export
    @GetMapping("/shop/{shopId}/export")
    @PreAuthorize("hasRole('SHOPKEEPER') or hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportCsv(@PathVariable Long shopId) {
        String csv = shortageListService.exportShortageListAsCsv(shopId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "shortage-list.csv");

        return ResponseEntity.ok()
                .headers(headers)
                .body(csv.getBytes());
    }
}
