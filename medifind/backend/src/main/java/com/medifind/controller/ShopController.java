package com.medifind.controller;

import com.medifind.model.Shop;
import com.medifind.model.User;
import com.medifind.repository.ShopRepository;
import com.medifind.repository.UserRepository;
import com.medifind.service.ShopAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.*;

@RestController
@RequestMapping("/api/shops")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class ShopController {

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final ShopAccessService shopAccessService;

    @GetMapping("/nearby")
    public ResponseEntity<List<NearbyShop>> nearby(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "3") double radiusKm) {
        if (!isValidCoordinate(lat, lng)) return ResponseEntity.badRequest().build();
        double safeRadius = Math.min(Math.max(radiusKm, 0.1), 3.0);
        List<NearbyShop> shops = shopRepository.findAll().stream()
                .filter(shop -> isValidCoordinate(shop.getLat(), shop.getLng()))
                .filter(shop -> !"open".equalsIgnoreCase(filter) || Boolean.TRUE.equals(shop.getIsOpen()))
                .filter(shop -> !"24hr".equalsIgnoreCase(filter) || Boolean.TRUE.equals(shop.getIs24hr()))
                .map(shop -> new NearbyShop(shop.getId(), shop.getName(), shop.getAddress(), shop.getLat(), shop.getLng(),
                        Boolean.TRUE.equals(shop.getIsOpen()), Boolean.TRUE.equals(shop.getIs24hr()), distanceKm(lat, lng, shop.getLat(), shop.getLng())))
                .filter(shop -> shop.distanceKm() <= safeRadius)
                .sorted(Comparator.comparingDouble(NearbyShop::distanceKm))
                .toList();
        return ResponseEntity.ok(shops);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShopPublicDetails> getShop(@PathVariable Long id) {
        return shopRepository.findById(id)
                .map(shop -> ResponseEntity.ok(new ShopPublicDetails(shop.getId(), shop.getName(), shop.getAddress(),
                        shop.getPhone(), shop.getLat(), shop.getLng(), Boolean.TRUE.equals(shop.getIsOpen()),
                        Boolean.TRUE.equals(shop.getIs24hr()))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Shop> addShop(@RequestBody Shop shop) {
        return ResponseEntity.ok(shopRepository.save(shop));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Shop> updateShop(@PathVariable Long id, @RequestBody Shop updated) {
        Shop shop = shopRepository.findById(id).orElseThrow(() -> new RuntimeException("Shop not found"));
        shop.setName(updated.getName());
        shop.setOwnerName(updated.getOwnerName());
        shop.setPhone(updated.getPhone());
        shop.setAddress(updated.getAddress());
        shop.setPincode(updated.getPincode());
        shop.setIsOpen(updated.getIsOpen());
        shop.setIs24hr(updated.getIs24hr());
        return ResponseEntity.ok(shopRepository.save(shop));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteShop(@PathVariable Long id) {
        shopRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Shop>> allShops() {
        return ResponseEntity.ok(shopRepository.findAll());
    }

    @PatchMapping("/{shopId}/status")
    @PreAuthorize("hasRole('SHOPKEEPER')")
    public ResponseEntity<?> toggleStatus(@PathVariable Long shopId, @RequestParam Boolean open, Authentication authentication) {
        Shop shop = shopAccessService.requireOwnedShop(shopId, authentication);
        shop.setIsOpen(open);
        shopRepository.save(shop);
        return ResponseEntity.ok(Map.of("shopId", shopId, "isOpen", open));
    }

    private static boolean isValidCoordinate(Double lat, Double lng) {
        return lat != null && lng != null && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    private static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Minimal public response used by customer pharmacy discovery. */
    public record NearbyShop(Long id, String name, String address, Double lat, Double lng,
                             boolean isOpen, boolean is24hr, double distanceKm) { }
    public record ShopPublicDetails(Long id, String name, String address, String phone, Double lat, Double lng,
                                    boolean isOpen, boolean is24hr) { }
}
