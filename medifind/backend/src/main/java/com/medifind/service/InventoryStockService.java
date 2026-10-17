package com.medifind.service;

import com.medifind.model.Inventory;
import com.medifind.model.InvoiceItem;
import com.medifind.repository.InvoiceRepository;
import com.medifind.repository.InvoiceItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;

/** Maintains a single, safe stock representation for both whole packs and partial tablet/capsule sales. */
@Service
@RequiredArgsConstructor
public class InventoryStockService {
    private final InvoiceItemRepository invoiceItemRepository;
    private final InvoiceRepository invoiceRepository;

    public void hydrateLegacyInventory(Inventory inventory) {
        if (inventory.getQuantityInBaseUnits() != null) return;
        invoiceItemRepository.findTopByShopIdAndProductNameIgnoreCaseOrderByIdDesc(
                        inventory.getShopId(), inventory.getMedicineName())
                .ifPresentOrElse(item -> copyInvoiceMetadata(inventory, item, null),
                        () -> {
                            inventory.setPackSize(1);
                            inventory.setBaseUnit(safeUnit(inventory.getUnit()));
                            inventory.setQuantityInBaseUnits(nonNegative(inventory.getQuantity()));
                        });
    }

    public void receiveStock(Inventory inventory, InvoiceItem item, String agencyName) {
        if (inventory.getQuantityInBaseUnits() == null && inventory.getId() != null) hydrateLegacyInventory(inventory);
        if (inventory.getQuantityInBaseUnits() == null) inventory.setQuantityInBaseUnits(0);
        int existing = nonNegative(inventory.getQuantityInBaseUnits());
        copyInvoiceMetadata(inventory, item, agencyName);
        int received = nonNegative(item.getQuantity()) * safePackSize(inventory);
        inventory.setQuantityInBaseUnits(existing + received);
        refreshLegacyQuantity(inventory);
    }

    public void copyInvoiceMetadata(Inventory inventory, InvoiceItem item, String agencyName) {
        PackInfo packInfo = parsePack(item.getPack(), item.getUnit());
        inventory.setPack(item.getPack());
        inventory.setPackSize(packInfo.size());
        inventory.setBaseUnit(packInfo.unit());
        inventory.setUnit(packInfo.size() > 1 ? "strips" : packInfo.unit());
        inventory.setBatchNo(item.getBatchNo());
        inventory.setExpiryDate(item.getExpiryDate());
        inventory.setManufacturer(item.getManufacturer());
        inventory.setHsnCode(item.getHsnCode());
        inventory.setCgstRate(item.getCgstRate());
        inventory.setSgstRate(item.getSgstRate());
        String source = agencyName;
        if (source == null || source.isBlank()) {
            source = invoiceRepository.findById(item.getInvoiceId()).map(invoice -> invoice.getAgencyName()).orElse(null);
        }
        if (source != null && !source.isBlank()) inventory.setSourceAgency(source);
        if (item.getRate() != null && item.getRate().signum() > 0) inventory.setPrice(item.getRate());
        if (inventory.getQuantityInBaseUnits() == null) {
            inventory.setQuantityInBaseUnits(nonNegative(inventory.getQuantity()) * packInfo.size());
            refreshLegacyQuantity(inventory);
        }
    }

    public int availableBaseUnits(Inventory inventory) {
        hydrateLegacyInventory(inventory);
        return nonNegative(inventory.getQuantityInBaseUnits());
    }

    public void reduceBaseUnits(Inventory inventory, int quantity) {
        int remaining = availableBaseUnits(inventory) - quantity;
        if (remaining < 0) throw new IllegalArgumentException("Stock cannot be negative.");
        inventory.setQuantityInBaseUnits(remaining);
        refreshLegacyQuantity(inventory);
    }

    public BigDecimal defaultUnitPrice(Inventory inventory) {
        int size = safePackSize(inventory);
        BigDecimal packPrice = inventory.getPrice() == null ? BigDecimal.ZERO : inventory.getPrice();
        return packPrice.divide(BigDecimal.valueOf(size), 2, java.math.RoundingMode.HALF_UP);
    }

    public String stockLabel(Inventory inventory) {
        int quantity = availableBaseUnits(inventory);
        int size = safePackSize(inventory);
        if (size <= 1) return quantity + " " + safeUnit(inventory.getBaseUnit());
        int fullPacks = quantity / size;
        int loose = quantity % size;
        String base = quantity + " " + inventory.getBaseUnit() + (quantity == 1 ? "" : "s");
        return loose == 0 ? base + " (" + fullPacks + " strips)" : base + " (" + fullPacks + " strips + " + loose + ")";
    }

    private void refreshLegacyQuantity(Inventory inventory) {
        int size = safePackSize(inventory);
        inventory.setQuantity(size > 1 ? nonNegative(inventory.getQuantityInBaseUnits()) / size : nonNegative(inventory.getQuantityInBaseUnits()));
    }

    private int safePackSize(Inventory inventory) { return inventory.getPackSize() == null || inventory.getPackSize() < 1 ? 1 : inventory.getPackSize(); }
    private int nonNegative(Integer value) { return value == null ? 0 : Math.max(value, 0); }
    private String safeUnit(String value) { return value == null || value.isBlank() ? "unit" : value.trim().toLowerCase(Locale.ROOT); }

    private PackInfo parsePack(String pack, String fallbackUnit) {
        if (pack != null) {
            String compact = pack.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
            java.util.regex.Matcher match = java.util.regex.Pattern
                    .compile("^(\\d+)(TABS?|TABLETS?|CAPS?|CAPSULES?|S|STRIPS?)").matcher(compact);
            if (match.find()) {
                int size = Integer.parseInt(match.group(1));
                String suffix = match.group(2);
                return new PackInfo(size, suffix.startsWith("CAP") ? "capsule" : "tablet");
            }
        }
        return new PackInfo(1, safeUnit(fallbackUnit));
    }

    private record PackInfo(int size, String unit) { }
}
