package com.medifind.config;

import com.medifind.model.User;
import com.medifind.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class AdminDataInitializer {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${medifind.admin.email:admin@medifind.com}")
    private String adminEmail;

    @Value("${medifind.admin.password:admin123}")
    private String adminPassword;

    @Bean
    CommandLineRunner ensureDevelopmentAdmin() {
        return args -> {
            User admin = userRepository.findByEmail(adminEmail).orElseGet(() -> User.builder()
                    .name("MediFind Admin")
                    .email(adminEmail)
                    .phone("")
                    .role(User.Role.ADMIN)
                    .approvalStatus(User.ApprovalStatus.NOT_REQUIRED)
                    .build());

            admin.setRole(User.Role.ADMIN);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            if (admin.getApprovalStatus() == null) {
                admin.setApprovalStatus(User.ApprovalStatus.NOT_REQUIRED);
            }
            userRepository.save(admin);
        };
    }
}
