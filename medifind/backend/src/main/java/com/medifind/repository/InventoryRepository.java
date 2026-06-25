package com.medifind.repository;

import com.medifind.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    List<Inventory> findByShopId(Long shopId);
    Optional<Inventory> findByShopIdAndMedicineNameIgnoreCase(Long shopId, String medicineName);
    List<Inventory> findByShopIdAndQuantityGreaterThan(Long shopId, int qty);
}
