package com.medifind.service;

import com.medifind.model.Shop;
import com.medifind.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopRepository shopRepository;

    @Value("${medifind.search.radius.km:4}")
    private double radiusKm;

    // Get all shops near user (within 4km radius)
    public List<Shop> getNearbyShops(double lat, double lng) {
        return shopRepository.findNearbyShops(lat, lng, radiusKm);
    }

    // Get only open shops near user
    public List<Shop> getNearbyOpenShops(double lat, double lng) {
        return shopRepository.findNearbyOpenShops(lat, lng, radiusKm);
    }

    // Get only 24hr shops near user
    public List<Shop> getNearby24hrShops(double lat, double lng) {
        return shopRepository.findNearby24hrShops(lat, lng, radiusKm);
    }

    // Admin: Add new shop
    public Shop addShop(Shop shop) {
        return shopRepository.save(shop);
    }

    // Admin: Update shop details
    public Shop updateShop(Long id, Shop updated) {
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Shop not found: " + id));
        shop.setName(updated.getName());
        shop.setPhone(updated.getPhone());
        shop.setAddress(updated.getAddress());
        shop.setLat(updated.getLat());
        shop.setLng(updated.getLng());
        shop.setOpenTime(updated.getOpenTime());
        shop.setCloseTime(updated.getCloseTime());
        shop.setIs24hr(updated.getIs24hr());
        shop.setPincode(updated.getPincode());
        shop.setLicenseNumber(updated.getLicenseNumber());
        return shopRepository.save(shop);
    }

    // Shopkeeper: Toggle open/close status (real-time)
    public Shop toggleOpenStatus(Long shopId, boolean isOpen) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop not found: " + shopId));
        shop.setIsOpen(isOpen);
        return shopRepository.save(shop);
    }

    // Record a shop view for analytics
    public void recordView(Long shopId) {
        shopRepository.findById(shopId).ifPresent(shop -> {
            shop.setViews(shop.getViews() + 1);
            shopRepository.save(shop);
        });
    }

    // Get shop by ID
    public Shop getShopById(Long id) {
        return shopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Shop not found: " + id));
    }

    // Admin: Delete shop
    public void deleteShop(Long id) {
        shopRepository.deleteById(id);
    }

    // All shops (admin view)
    public List<Shop> getAllShops() {
        return shopRepository.findAll();
    }
}
