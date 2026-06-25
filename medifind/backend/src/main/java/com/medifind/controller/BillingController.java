package com.medifind.controller;

import com.medifind.model.SaleBill;
import com.medifind.model.Shop;
import com.medifind.repository.SaleBillRepository;
import com.medifind.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasRole('SHOPKEEPER') or hasRole('ADMIN')")
public class BillingController {
    private final SaleBillRepository billRepository;
    private final ShopRepository shopRepository;

    @PostMapping("/shop/{shopId}")
    public ResponseEntity<?> create(@PathVariable Long shopId, @RequestBody SaleBill bill) {
        Shop shop = shopRepository.findById(shopId).orElse(null);
        if (shop == null) return ResponseEntity.badRequest().body("Shop not found");
        bill.setId(null);
        bill.setShopId(shopId);
        bill.setShopName(shop.getName());
        bill.setPincode(shop.getPincode());
        bill.setBilledAt(LocalDateTime.now());
        BigDecimal price = value(bill.getPrice()).multiply(BigDecimal.valueOf(bill.getQuantity() == null ? 1 : bill.getQuantity()));
        bill.setTotal(price.add(value(bill.getSgst())).add(value(bill.getCgst())).add(value(bill.getServiceTax())));
        return ResponseEntity.ok(billRepository.save(bill));
    }

    @GetMapping("/shop/{shopId}/daily")
    public ResponseEntity<?> daily(@PathVariable Long shopId, @RequestParam(required = false) LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        List<SaleBill> bills = billRepository.findByShopIdAndBilledAtBetweenOrderByBilledAtDesc(shopId, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        BigDecimal revenue = bills.stream().map(SaleBill::getTotal).map(this::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        int medicines = bills.stream().mapToInt(b -> b.getQuantity() == null ? 0 : b.getQuantity()).sum();
        return ResponseEntity.ok(Map.of("date", day, "bills", bills, "billCount", bills.size(), "medicinesSold", medicines, "revenue", revenue));
    }

    private BigDecimal value(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
