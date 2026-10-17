package com.medifind.service;

import com.medifind.model.Shop;
import com.medifind.model.User;
import com.medifind.repository.ShopRepository;
import com.medifind.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ShopAccessService {
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;

    /** Ensures a shopkeeper can only use the shop associated with their login. */
    public Shop requireOwnedShop(Long shopId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required.");
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user was not found."));
        Shop shop = shopRepository.findByCreatedById(user.getId())
                .orElseThrow(() -> new AccessDeniedException("No shop is linked to this account."));
        if (!shop.getId().equals(shopId)) {
            throw new AccessDeniedException("You are not allowed to access this shop's inventory.");
        }
        return shop;
    }
}
