package com.medifind.controller;

import com.medifind.model.Shop;
import com.medifind.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/shops")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class ShopController {

    private final ShopService shopService;

    // PUBLIC: Get nearby shops (customer uses this on map page)
    // GET /api/shops/nearby?lat=17.385&lng=78.486&filter=open
    @GetMapping("/nearby")
    public ResponseEntity<List<Shop>> getNearbyShops(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "all") String filter) {

        List<Shop> shops = switch (filter) {
            case "open" -> shopService.getNearbyOpenShops(lat, lng);
            case "24hr" -> shopService.getNearby24hrShops(lat, lng);
            default -> shopService.getNearbyShops(lat, lng);
        };
        return ResponseEntity.ok(shops);
    }

    // PUBLIC: Get single shop details + record view
    // GET /api/shops/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Shop> getShop(@PathVariable Long id) {
        shopService.recordView(id); // analytics
        return ResponseEntity.ok(shopService.getShopById(id));
    }

    // ADMIN ONLY: Add new shop
    // POST /api/shops
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Shop> addShop(@RequestBody Shop shop) {
        return ResponseEntity.ok(shopService.addShop(shop));
    }

    // ADMIN ONLY: Update shop
    // PUT /api/shops/{id}
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Shop> updateShop(@PathVariable Long id, @RequestBody Shop shop) {
        return ResponseEntity.ok(shopService.updateShop(id, shop));
    }

    // ADMIN ONLY: Delete shop
    // DELETE /api/shops/{id}
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteShop(@PathVariable Long id) {
        shopService.deleteShop(id);
        return ResponseEntity.noContent().build();
    }

    // ADMIN: Get all shops
    // GET /api/shops/all
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Shop>> getAllShops() {
        return ResponseEntity.ok(shopService.getAllShops());
    }

    // SHOPKEEPER: Toggle open/close status
    // PATCH /api/shops/{id}/status?open=true
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('SHOPKEEPER') or hasRole('ADMIN')")
    public ResponseEntity<Shop> toggleStatus(@PathVariable Long id,
                                              @RequestParam boolean open) {
        return ResponseEntity.ok(shopService.toggleOpenStatus(id, open));
    }
}
