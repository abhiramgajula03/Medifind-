package com.medifind.controller;

import com.medifind.config.JwtUtil;
import com.medifind.model.User;
import com.medifind.repository.UserRepository;
import com.medifind.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ShopRepository shopRepository;

    // POST /api/auth/register
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        if (userRepository.existsByEmail(body.get("email"))) {
            return ResponseEntity.badRequest().body("Email already registered");
        }

        User.Role requestedRole = "SHOPKEEPER".equals(body.get("role")) ? User.Role.SHOPKEEPER : User.Role.CUSTOMER;
        User user = User.builder()
                .name(body.get("name"))
                .email(body.get("email"))
                .password(passwordEncoder.encode(body.get("password")))
                .phone(body.get("phone"))
                .role(requestedRole)
                .shopName(body.get("shopName"))
                .shopAddress(body.get("shopAddress"))
                .pincode(body.get("pincode"))
                .licenseNumber(body.get("licenseNumber"))
                .licensePhoto(body.get("licensePhoto"))
                .approvalStatus(requestedRole == User.Role.SHOPKEEPER ? User.ApprovalStatus.PENDING : User.ApprovalStatus.NOT_REQUIRED)
                .build();

        userRepository.save(user);
        if (requestedRole == User.Role.SHOPKEEPER) {
            return ResponseEntity.ok(Map.of("role", user.getRole(), "name", user.getName(), "approvalStatus", user.getApprovalStatus(), "message", "Application submitted for admin approval"));
        }
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return ResponseEntity.ok(Map.of("token", token, "role", user.getRole(), "name", user.getName(), "userId", user.getId()));
    }

    // POST /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        User user = userRepository.findByEmail(body.get("email"))
                .orElse(null);

        if (user == null || !passwordEncoder.matches(body.get("password"), user.getPassword())) {
            return ResponseEntity.status(401).body("Invalid email or password");
        }

        if (user.getRole() == User.Role.SHOPKEEPER && user.getApprovalStatus() != User.ApprovalStatus.APPROVED) {
            String status = user.getApprovalStatus() == null ? "PENDING" : user.getApprovalStatus().name();
            return ResponseEntity.status(403).body("Shopkeeper application is " + status.toLowerCase() + ". Admin approval is required.");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        Map<String, Object> response = new java.util.HashMap<>(Map.of(
                "token", token,
                "role", user.getRole(),
                "name", user.getName(),
                "userId", user.getId()
        ));
        shopRepository.findByCreatedById(user.getId()).ifPresent(shop -> response.put("shopId", shop.getId()));
        return ResponseEntity.ok(response);
    }
}
