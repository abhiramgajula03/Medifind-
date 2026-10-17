package com.medifind.controller;

import com.medifind.model.Inventory;
import com.medifind.model.Shop;
import com.medifind.repository.InventoryRepository;
import com.medifind.repository.ShopRepository;
import com.medifind.service.InventoryStockService;
import com.medifind.service.MedicineImageOcrService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/customer/pharmacies")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class CustomerPharmacyController {
    private final ShopRepository shopRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryStockService stockService;
    private final MedicineImageOcrService imageOcrService;

    @GetMapping("/{shopId}")
    public ResponseEntity<ShopDetails> details(@PathVariable Long shopId) {
        return shopRepository.findById(shopId)
                .map(shop -> ResponseEntity.ok(toShopDetails(shop)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{shopId}/medicines")
    public ResponseEntity<List<MedicineOption>> search(@PathVariable Long shopId, @RequestParam String query) {
        if (!shopRepository.existsById(shopId)) return ResponseEntity.notFound().build();
        String term = query == null ? "" : query.trim();
        if (term.length() < 2 || term.length() > 100) return ResponseEntity.ok(List.of());
        List<MedicineOption> matches = inventoryRepository.findByShopIdAndMedicineNameContainingIgnoreCase(shopId, term)
                .stream().limit(12).map(this::toMedicineOption).toList();
        return ResponseEntity.ok(matches);
    }

    @GetMapping("/{shopId}/medicines/{inventoryId}")
    public ResponseEntity<Availability> availability(@PathVariable Long shopId, @PathVariable Long inventoryId) {
        if (!shopRepository.existsById(shopId)) return ResponseEntity.notFound().build();
        return inventoryRepository.findByIdAndShopId(inventoryId, shopId)
                .map(item -> {
                    MedicineOption medicine = toMedicineOption(item);
                    return ResponseEntity.ok(new Availability(medicine,
                            medicine.available() ? "This medicine is listed as available. Stock reflects the pharmacy's latest recorded inventory; please confirm with the shopkeeper before travelling."
                                    : "This medicine is listed in the pharmacy's inventory, but it is currently out of stock."));
                }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/{shopId}/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> identify(@PathVariable Long shopId, @RequestPart("image") MultipartFile image) {
        if (!shopRepository.existsById(shopId)) return ResponseEntity.notFound().build();
        if (image == null || image.isEmpty() || image.getSize() > 5 * 1024 * 1024)
            return ResponseEntity.badRequest().body(new Message("Choose an image smaller than 5 MB."));
        if (image.getContentType() == null || !(image.getContentType().equalsIgnoreCase("image/jpeg") || image.getContentType().equalsIgnoreCase("image/png")))
            return ResponseEntity.badRequest().body(new Message("Please upload a JPEG or PNG image."));
        try {
            List<MedicineOption> suggestions = imageOcrService.identify(image, shopId).stream().map(this::toMedicineOption).toList();
            return ResponseEntity.ok(new Identification(suggestions, suggestions.isEmpty()
                    ? "I couldn't read a clear medicine name. Try a sharper photo or type the name." 
                    : "These are possible matches from this pharmacy. Please choose the exact product."));
        } catch (Exception ex) {
            return ResponseEntity.status(503).body(new Message("Photo identification is temporarily unavailable. You can enter the medicine name instead."));
        }
    }

    private ShopDetails toShopDetails(Shop shop) {
        return new ShopDetails(shop.getId(), shop.getName(), shop.getAddress(), shop.getPhone(), shop.getLat(), shop.getLng(),
                Boolean.TRUE.equals(shop.getIsOpen()), Boolean.TRUE.equals(shop.getIs24hr()));
    }

    @Transactional(readOnly = true)
    public MedicineOption toMedicineOption(Inventory item) {
        int quantity = stockService.availableBaseUnits(item);
        String unit = item.getBaseUnit() == null || item.getBaseUnit().isBlank()
                ? (item.getUnit() == null ? "unit" : item.getUnit()) : item.getBaseUnit();
        return new MedicineOption(item.getId(), item.getMedicineName(), quantity, unit, stockService.stockLabel(item),
                item.getPrice(), quantity > 0);
    }

    public record ShopDetails(Long id, String name, String address, String phone, Double lat, Double lng,
                              boolean isOpen, boolean is24hr) { }
    public record MedicineOption(Long id, String name, int quantity, String unit, String stockLabel,
                                 BigDecimal price, boolean available) { }
    public record Availability(MedicineOption medicine, String message) { }
    public record Identification(List<MedicineOption> suggestions, String message) { }
    public record Message(String message) { }
}
