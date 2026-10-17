package com.medifind.repository;

import com.medifind.model.ShopAgency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShopAgencyRepository extends JpaRepository<ShopAgency, Long> {
    List<ShopAgency> findByShopId(Long shopId);
    Optional<ShopAgency> findByShopIdAndAgencyId(Long shopId, Long agencyId);
    boolean existsByShopIdAndAgencyId(Long shopId, Long agencyId);
}
