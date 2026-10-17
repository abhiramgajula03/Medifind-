package com.medifind.config;

import com.medifind.model.Inventory;
import com.medifind.repository.InventoryRepository;
import com.medifind.service.InventoryStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Fills the new base-unit fields from existing invoice items exactly once, without touching history. */
@Component
@RequiredArgsConstructor
public class InventoryStockMigrationRunner implements ApplicationRunner {
    private final InventoryRepository inventoryRepository;
    private final InventoryStockService inventoryStockService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Inventory inventory : inventoryRepository.findAll()) {
            if (inventory.getQuantityInBaseUnits() == null) {
                inventoryStockService.hydrateLegacyInventory(inventory);
            }
        }
    }
}
