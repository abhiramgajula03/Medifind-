package com.medifind.controller;

import com.medifind.config.JwtUtil;
import com.medifind.model.Shop;
import com.medifind.model.User;
import com.medifind.repository.ShopRepository;
import com.medifind.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final ShopRepository shopRepository;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        Map<String, Object> res = authService.register(user);
        if (res.containsKey("error")) {
            return ResponseEntity.badRequest().body(res);
        }
        return ResponseEntity.ok(res);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        User user = authService.login(credentials.get("email"), credentials.get("password"));
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password"));
        }
        if (user.getRole() == User.Role.SHOPKEEPER) {
            if (user.getApprovalStatus() == User.ApprovalStatus.PENDING) {
                return ResponseEntity.status(403).body(Map.of("error", "Your shopkeeper account application is pending Admin approval."));
            }
            if (user.getApprovalStatus() == User.ApprovalStatus.REJECTED) {
                return ResponseEntity.status(403).body(Map.of("error", "Your shopkeeper account application was rejected by Admin."));
            }
        }
        String token = jwtUtil.generateToken(user);
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("role", user.getRole().name());
        response.put("name", user.getName());
        response.put("userId", user.getId());
        shopRepository.findByCreatedById(user.getId()).map(Shop::getId)
                .ifPresent(shopId -> response.put("shopId", shopId));
        return ResponseEntity.ok(response);
    }
}
