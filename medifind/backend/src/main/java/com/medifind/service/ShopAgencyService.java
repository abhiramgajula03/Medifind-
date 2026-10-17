package com.medifind.service;

import com.medifind.model.ShopAgency;
import com.medifind.model.Agency;
import com.medifind.model.Shop;
import com.medifind.repository.AgencyRepository;
import com.medifind.repository.ShopAgencyRepository;
import com.medifind.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShopAgencyService {

    private final ShopAgencyRepository shopAgencyRepository;
    private final AgencyRepository agencyRepository;
    private final ShopRepository shopRepository;

    /**
     * Creates a supplier only after the user explicitly requests it and always
     * links it to the current shop in the same transaction.  Equivalent names
     * reuse the existing global agency instead of creating a duplicate.
     */
    @Transactional
    public ShopAgency registerAgencyForShop(Long shopId, String rawName) {
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        if (name.length() < 3) throw new IllegalArgumentException("Enter a valid agency name.");

        String normalized = normalizeName(name);
        Agency agency = agencyRepository.findAll().stream()
                .filter(candidate -> normalizeName(candidate.getName()).equals(normalized))
                .findFirst()
                .orElseGet(() -> agencyRepository.save(Agency.builder()
                        .name(name)
                        // Agency currently requires an email and address. These placeholders
                        // preserve that schema; the administrator can complete them later.
                        .email("pending-agency-" + UUID.randomUUID() + "@local.medifind")
                        .address("Not provided")
                        .isActive(true)
                        .build()));

        return shopAgencyRepository.findByShopIdAndAgencyId(shopId, agency.getId())
                .orElseGet(() -> shopAgencyRepository.save(ShopAgency.builder()
                        .shopId(shopId)
                        .shopName(shopRepository.findById(shopId).map(Shop::getName).orElse("Unknown"))
                        .agencyId(agency.getId())
                        .agencyName(agency.getName())
                        .isActive(true)
                        .build()));
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").replaceAll("\\s+", " ").trim();
    }

    public ShopAgency addAgencyToShop(Long shopId, String shopName, Long agencyId, String agencyName) {
        if (shopAgencyRepository.existsByShopIdAndAgencyId(shopId, agencyId)) {
            throw new RuntimeException("Agency already added to this shop");
        }
        return shopAgencyRepository.save(ShopAgency.builder()
                .shopId(shopId)
                .shopName(shopName)
                .agencyId(agencyId)
                .agencyName(agencyName)
                .isActive(true)
                .build());
    }

    public List<ShopAgency> getAgenciesForShop(Long shopId) {
        return shopAgencyRepository.findByShopId(shopId);
    }

    public void removeAgencyFromShop(Long shopId, Long agencyId) {
        shopAgencyRepository.findByShopIdAndAgencyId(shopId, agencyId)
                .ifPresent(shopAgencyRepository::delete);
    }

    public ShopAgency updateAgencyStatus(Long shopId, Long agencyId, Boolean isActive) {
        ShopAgency shopAgency = shopAgencyRepository.findByShopIdAndAgencyId(shopId, agencyId)
                .orElseThrow(() -> new RuntimeException("Agency not found for this shop"));
        shopAgency.setIsActive(isActive);
        return shopAgencyRepository.save(shopAgency);
    }
}
