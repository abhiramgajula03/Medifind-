package com.medifind.controller;

import com.medifind.model.Shop;
import com.medifind.model.User;
import com.medifind.repository.ShopRepository;
import com.medifind.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;

    @GetMapping("/shopkeeper-applications")
    public List<Map<String, Object>> applications() {
        return userRepository.findAll().stream().filter(u -> u.getRole() == User.Role.SHOPKEEPER).map(this::view).toList();
    }

    @PatchMapping("/shopkeeper-applications/{id}")
    public ResponseEntity<?> review(@PathVariable Long id, @RequestBody Map<String, String> body) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null || user.getRole() != User.Role.SHOPKEEPER) return ResponseEntity.notFound().build();
        User.ApprovalStatus status = User.ApprovalStatus.valueOf(body.get("status"));
        user.setApprovalStatus(status);
        userRepository.save(user);
        if (status == User.ApprovalStatus.APPROVED && shopRepository.findByCreatedById(id).isEmpty()) {
            Shop shop = Shop.builder().name(user.getShopName()).ownerName(user.getName()).phone(user.getPhone())
                    .address(user.getShopAddress()).pincode(user.getPincode()).licenseNumber(user.getLicenseNumber())
                    .lat(Double.valueOf(body.getOrDefault("lat", "17.385"))).lng(Double.valueOf(body.getOrDefault("lng", "78.486")))
                    .isOpen(false).is24hr(false).createdBy(user).build();
            shopRepository.save(shop);
        }
        return ResponseEntity.ok(view(user));
    }

    private Map<String, Object> view(User user) {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("id", user.getId()); result.put("name", user.getName()); result.put("email", user.getEmail());
        result.put("phone", user.getPhone() == null ? "" : user.getPhone()); result.put("shopName", user.getShopName() == null ? "" : user.getShopName());
        result.put("shopAddress", user.getShopAddress() == null ? "" : user.getShopAddress()); result.put("pincode", user.getPincode() == null ? "" : user.getPincode());
        result.put("licenseNumber", user.getLicenseNumber() == null ? "" : user.getLicenseNumber()); result.put("licensePhoto", user.getLicensePhoto() == null ? "" : user.getLicensePhoto());
        result.put("approvalStatus", user.getApprovalStatus() == null ? "PENDING" : user.getApprovalStatus().name());
        return result;
    }
}
