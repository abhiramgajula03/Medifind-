package com.medifind.controller;

import com.medifind.model.Inventory;
import com.medifind.model.Invoice;
import com.medifind.model.InvoiceItem;
import com.medifind.service.BillScanService;
import com.medifind.service.ShopAccessService;
import com.medifind.service.InventoryStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class InventoryController {

    private final BillScanService billScanService;
    private final ShopAccessService shopAccessService;
    private final InventoryStockService inventoryStockService;

    @GetMapping("/shop/{shopId}")
    @PreAuthorize("hasRole('SHOPKEEPER')")
    public ResponseEntity<List<Inventory>> getShopInventory(@PathVariable Long shopId, Authentication authentication) {
        shopAccessService.requireOwnedShop(shopId, authentication);
        return ResponseEntity.ok(billScanService.getInventoryByShop(shopId));
    }

    @GetMapping("/shop/{shopId}/search")
    @PreAuthorize("hasRole('SHOPKEEPER')")
    public ResponseEntity<List<Inventory>> searchInventory(
            @PathVariable Long shopId,
            @RequestParam String query, Authentication authentication) {
        shopAccessService.requireOwnedShop(shopId, authentication);
        return ResponseEntity.ok(billScanService.searchInventory(shopId, query));
    }

    @GetMapping("/shop/{shopId}/agency/{agencyId}")
    @PreAuthorize("hasRole('SHOPKEEPER')")
    public ResponseEntity<List<Inventory>> getAgencyInventory(
            @PathVariable Long shopId,
            @PathVariable Long agencyId, Authentication authentication) {
        shopAccessService.requireOwnedShop(shopId, authentication);
        return ResponseEntity.ok(billScanService.getInventoryByShopAndAgency(shopId, agencyId));
    }

    @GetMapping("/shop/{shopId}/agencies")
    @PreAuthorize("hasRole('SHOPKEEPER')")
    public ResponseEntity<Map<String, List<Inventory>>> getInventoryByAgencies(@PathVariable Long shopId, Authentication authentication) {
        shopAccessService.requireOwnedShop(shopId, authentication);
        return ResponseEntity.ok(billScanService.getInventoryGroupedByAgency(shopId));
    }

    @GetMapping("/shop/{shopId}/summary")
    @PreAuthorize("hasRole('SHOPKEEPER')")
    public ResponseEntity<Map<String, Object>> getInventorySummary(@PathVariable Long shopId, Authentication authentication) {
        shopAccessService.requireOwnedShop(shopId, authentication);
        List<Inventory> inventory = billScanService.getInventoryByShop(shopId);
        long totalItems = inventory.size();
        long totalQuantity = inventory.stream()
                .mapToLong(inventoryStockService::availableBaseUnits)
                .sum();
        BigDecimal totalValue = inventory.stream()
                .filter(inv -> inv.getPrice() != null)
                .map(inv -> inventoryStockService.defaultUnitPrice(inv)
                        .multiply(BigDecimal.valueOf(inventoryStockService.availableBaseUnits(inv))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long lowStock = inventory.stream()
                .filter(inv -> inventoryStockService.availableBaseUnits(inv) > 0 && inventoryStockService.availableBaseUnits(inv) < 10)
                .count();

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalItems", totalItems);
        summary.put("totalQuantity", totalQuantity);
        summary.put("totalValue", totalValue);
        summary.put("lowStock", lowStock);
        summary.put("outOfStock", inventory.stream().filter(inv -> inventoryStockService.availableBaseUnits(inv) == 0).count());
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/shop/{shopId}/sources")
    @PreAuthorize("hasRole('SHOPKEEPER')")
    public ResponseEntity<Map<String, List<String>>> getInventorySources(@PathVariable Long shopId, Authentication authentication) {
        shopAccessService.requireOwnedShop(shopId, authentication);
        return ResponseEntity.ok(billScanService.getInventorySources(shopId));
    }

    @DeleteMapping("/shop/{shopId}")
    @PreAuthorize("hasRole('SHOPKEEPER')")
    public ResponseEntity<Map<String, Object>> clearShopInventory(@PathVariable Long shopId, Authentication authentication) {
        shopAccessService.requireOwnedShop(shopId, authentication);
        long deleted = billScanService.clearInventoryForShop(shopId);
        return ResponseEntity.ok(Map.of("deletedRecords", deleted, "message", "Current shop inventory cleared."));
    }
}
