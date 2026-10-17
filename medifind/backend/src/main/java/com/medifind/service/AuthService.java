package com.medifind.service;

import com.medifind.model.User;
import com.medifind.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public Map<String, Object> register(User user) {
        Map<String, Object> result = new HashMap<>();
        if (user == null || isBlank(user.getName()) || isBlank(user.getEmail()) || isBlank(user.getPassword())) {
            result.put("error", "Name, email, and password are required.");
            return result;
        }
        user.setName(user.getName().trim());
        user.setEmail(user.getEmail().trim().toLowerCase());
        if (user.getPassword().length() < 6) {
            result.put("error", "Password must contain at least 6 characters.");
            return result;
        }
        if (user.getRole() == null) user.setRole(User.Role.CUSTOMER);
        if (userRepository.existsByEmail(user.getEmail())) {
            result.put("error", "Email is already registered");
            return result;
        }
        if (user.getRole() == User.Role.SHOPKEEPER) {
            user.setApprovalStatus(User.ApprovalStatus.PENDING);
        } else {
            user.setApprovalStatus(User.ApprovalStatus.NOT_REQUIRED);
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);
        result.put("message", "Account created successfully");
        return result;
    }

    public User login(String email, String password) {
        if (isBlank(email) || isBlank(password)) return null;
        User user = userRepository.findByEmail(email.trim().toLowerCase()).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            return null;
        }
        return user;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
