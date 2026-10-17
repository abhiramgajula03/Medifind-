package com.medifind.service;

import com.medifind.model.SaleBill;
import com.medifind.model.Shop;
import com.medifind.model.Inventory;
import com.medifind.repository.InventoryRepository;
import com.medifind.repository.SaleBillRepository;
import com.medifind.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillingService {

    private final SaleBillRepository saleBillRepository;
    private final ShopRepository shopRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryStockService inventoryStockService;

    @Transactional
    public SaleBill createBill(Long shopId, Map<String, Object> data) {
        return createBillLine(shopId, data, null);
    }

    /** Saves a customer receipt as multiple existing SaleBill line records under one receipt number. */
    @Transactional
    @SuppressWarnings("unchecked")
    public List<SaleBill> createReceipt(Long shopId, Map<String, Object> data) {
        Object rawLines = data.get("lines");
        if (!(rawLines instanceof List<?> lines) || lines.isEmpty()) {
            throw new IllegalArgumentException("Add at least one medicine to the bill.");
        }
        String receiptNo = "SB-" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .format(java.time.LocalDateTime.now()) + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String customerName = String.valueOf(data.getOrDefault("customerName", ""));
        List<SaleBill> saved = new ArrayList<>();
        for (Object rawLine : lines) {
            if (!(rawLine instanceof Map<?, ?> rawMap)) throw new IllegalArgumentException("Invalid bill line.");
            Map<String, Object> line = new HashMap<>();
            rawMap.forEach((key, value) -> line.put(String.valueOf(key), value));
            line.put("customerName", customerName);
            saved.add(createBillLine(shopId, line, receiptNo));
        }
        return saved;
    }

    private SaleBill createBillLine(Long shopId, Map<String, Object> data, String receiptNo) {
        Shop shop = shopRepository.findById(shopId).orElseThrow(() -> new RuntimeException("Shop not found"));
        String medicineName = String.valueOf(data.getOrDefault("medicineName", "")).trim();
        int quantity = Integer.parseInt(data.getOrDefault("quantity", 0).toString());
        if (medicineName.isBlank()) throw new IllegalArgumentException("Select a medicine from current inventory.");
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be at least 1.");

        Long inventoryId = longValue(data.get("inventoryId"));
        // A pessimistic lock makes validation and decrement one atomic operation.
        Inventory inventory = inventoryId == null
                ? inventoryRepository.findLockedByShopIdAndMedicineNameIgnoreCase(shopId, medicineName)
                    .orElseThrow(() -> new IllegalArgumentException(medicineName + " is not currently available in inventory."))
                : inventoryRepository.findLockedByIdAndShopId(inventoryId, shopId)
                    .orElseThrow(() -> new IllegalArgumentException("Selected medicine is not in this shop's inventory."));
        inventoryStockService.hydrateLegacyInventory(inventory);
        int available = inventoryStockService.availableBaseUnits(inventory);
        if (quantity > available) {
            throw new IllegalArgumentException("Only " + inventoryStockService.stockLabel(inventory) + " of " + inventory.getMedicineName() + " are available.");
        }
        BigDecimal price = decimalValue(data.get("price"));
        if (price == null) price = inventoryStockService.defaultUnitPrice(inventory);
        if (price.signum() < 0) throw new IllegalArgumentException("Selling price cannot be negative.");
        BigDecimal lineAmount = price.multiply(BigDecimal.valueOf(quantity)).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal sgstRate = zero(inventory.getSgstRate());
        BigDecimal cgstRate = zero(inventory.getCgstRate());
        BigDecimal sgst = lineAmount.multiply(sgstRate).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal cgst = lineAmount.multiply(cgstRate).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal total = lineAmount.add(sgst).add(cgst);

        SaleBill bill = SaleBill.builder()
                .shopId(shopId)
                .shopName(shop.getName())
                .pincode(shop.getPincode())
                .customerName((String) data.getOrDefault("customerName", ""))
                .medicineName(inventory.getMedicineName())
                .inventoryId(inventory.getId()).sellingUnit(inventory.getBaseUnit())
                .packSize(inventory.getPackSize()).pack(inventory.getPack()).batchNo(inventory.getBatchNo())
                .expiryDate(inventory.getExpiryDate()).manufacturer(inventory.getManufacturer()).hsnCode(inventory.getHsnCode())
                .receiptNo(receiptNo)
                .quantity(quantity)
                .price(price.setScale(2, java.math.RoundingMode.HALF_UP)).sgst(sgst).cgst(cgst)
                .cgstRate(cgstRate).sgstRate(sgstRate).serviceTax(BigDecimal.ZERO).total(total)
                .build();
        SaleBill saved = saleBillRepository.save(bill);
        inventoryStockService.reduceBaseUnits(inventory, quantity);
        inventoryRepository.save(inventory);
        return saved;
    }

    public Map<String, Object> getDailyBills(Long shopId, LocalDate date) {
        List<SaleBill> bills = saleBillRepository.findByShopIdAndBilledAtBetween(
                shopId,
                date.atStartOfDay(),
                date.plusDays(1).atStartOfDay()
        );
        long medicinesSold = bills.stream().mapToLong(b -> b.getQuantity()).sum();
        BigDecimal revenue = bills.stream().map(SaleBill::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> result = new HashMap<>();
        result.put("bills", bills);
        result.put("billCount", bills.size());
        result.put("medicinesSold", medicinesSold);
        result.put("revenue", revenue);
        return result;
    }

    private Long longValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return Long.valueOf(String.valueOf(value)); } catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid inventory selection."); }
    }
    private BigDecimal decimalValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return new BigDecimal(String.valueOf(value)); } catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid selling price."); }
    }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
