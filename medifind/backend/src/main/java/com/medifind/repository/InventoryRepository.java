package com.medifind.repository;

import com.medifind.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    List<Inventory> findByShopId(Long shopId);
    Optional<Inventory> findByShopIdAndMedicineNameIgnoreCase(Long shopId, String medicineName);
    Optional<Inventory> findByIdAndShopId(Long id, Long shopId);
    List<Inventory> findByShopIdAndMedicineNameContainingIgnoreCase(Long shopId, String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select inventory from Inventory inventory where inventory.shopId = :shopId " +
            "and lower(inventory.medicineName) = lower(:medicineName)")
    Optional<Inventory> findLockedByShopIdAndMedicineNameIgnoreCase(
            @Param("shopId") Long shopId, @Param("medicineName") String medicineName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select inventory from Inventory inventory where inventory.id = :inventoryId and inventory.shopId = :shopId")
    Optional<Inventory> findLockedByIdAndShopId(@Param("inventoryId") Long inventoryId, @Param("shopId") Long shopId);

    @Modifying
    @Transactional
    long deleteByShopId(Long shopId);
}
