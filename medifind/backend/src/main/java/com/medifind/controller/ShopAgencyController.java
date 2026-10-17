package com.medifind.controller;

import com.medifind.model.ShopAgency;
import com.medifind.service.ShopAgencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/shop-agencies")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class ShopAgencyController {

    private final ShopAgencyService shopAgencyService;

    @PostMapping("/shop/{shopId}")
    @PreAuthorize("hasRole('SHOPKEEPER')")
    public ResponseEntity<ShopAgency> addAgency(@PathVariable Long shopId,
                                                @RequestBody java.util.Map<String, Object> body) {
        Long agencyId = Long.valueOf(body.get("agencyId").toString());
        String agencyName = (String) body.get("agencyName");
        String shopName = (String) body.get("shopName");
        return ResponseEntity.ok(shopAgencyService.addAgencyToShop(shopId, shopName, agencyId, agencyName));
    }

    @PostMapping("/shop/{shopId}/register")
    @PreAuthorize("hasRole('SHOPKEEPER')")
    public ResponseEntity<?> registerAgency(@PathVariable Long shopId,
                                            @RequestBody java.util.Map<String, Object> body) {
        try {
            return ResponseEntity.ok(shopAgencyService.registerAgencyForShop(shopId,
                    String.valueOf(body.getOrDefault("name", ""))));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(error.getMessage());
        }
    }

    @GetMapping("/shop/{shopId}")
    @PreAuthorize("hasRole('SHOPKEEPER')")
    public ResponseEntity<List<ShopAgency>> getAgencies(@PathVariable Long shopId) {
        return ResponseEntity.ok(shopAgencyService.getAgenciesForShop(shopId));
    }

    @DeleteMapping("/shop/{shopId}/agency/{agencyId}")
    @PreAuthorize("hasRole('SHOPKEEPER')")
    public ResponseEntity<Void> removeAgency(@PathVariable Long shopId, @PathVariable Long agencyId) {
        shopAgencyService.removeAgencyFromShop(shopId, agencyId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/shop/{shopId}/agency/{agencyId}")
    @PreAuthorize("hasRole('SHOPKEEPER')")
    public ResponseEntity<ShopAgency> updateStatus(@PathVariable Long shopId,
                                                    @PathVariable Long agencyId,
                                                    @RequestBody java.util.Map<String, Object> body) {
        Boolean isActive = Boolean.valueOf(body.get("isActive").toString());
        return ResponseEntity.ok(shopAgencyService.updateAgencyStatus(shopId, agencyId, isActive));
    }
}
