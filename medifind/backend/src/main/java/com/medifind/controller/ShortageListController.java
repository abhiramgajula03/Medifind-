package com.medifind.controller;

import com.medifind.model.ShortageList;
import com.medifind.model.User;
import com.medifind.repository.ShortageListRepository;
import com.medifind.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shortage")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class ShortageListController {

    private final ShortageListRepository shortageListRepository;
    private final UserRepository userRepository;

    @PostMapping("/shop/{shopId}/offline")
    public ResponseEntity<ShortageList> addOfflineShortage(@PathVariable Long shopId, @RequestBody ShortageList shortage) {
        shortage.setShopId(shopId);
        return ResponseEntity.ok(shortageListRepository.save(shortage));
    }

    @PostMapping("/shop/{shopId}/online")
    public ResponseEntity<ShortageList> requestOnline(@PathVariable Long shopId, @RequestBody ShortageList shortage) {
        shortage.setShopId(shopId);
        return ResponseEntity.ok(shortageListRepository.save(shortage));
    }

    @GetMapping("/shop/{shopId}")
    public ResponseEntity<List<ShortageList>> getShopShortages(@PathVariable Long shopId) {
        return ResponseEntity.ok(shortageListRepository.findByShopId(shopId));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<ShortageList>> getCustomerRequests(@PathVariable Long customerId) {
        return ResponseEntity.ok(shortageListRepository.findByCustomerId(customerId));
    }

    @PatchMapping("/{entryId}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long entryId, @RequestParam String status) {
        ShortageList entry = shortageListRepository.findById(entryId).orElseThrow(() -> new RuntimeException("Not found"));
        entry.setStatus(ShortageList.Status.valueOf(status));
        return ResponseEntity.ok(shortageListRepository.save(entry));
    }

    @GetMapping("/shop/{shopId}/export")
    public ResponseEntity<byte[]> exportShortageList(@PathVariable Long shopId) {
        List<ShortageList> list = shortageListRepository.findByShopId(shopId);
        String csv = "ID,Medicine,Customer,Qty,Status\n" + list.stream()
                .map(s -> s.getId() + "," + s.getMedicineName() + "," + s.getCustomerName() + "," + s.getQuantityNeeded() + "," + s.getStatus())
                .reduce((a, b) -> a + "\n" + b).orElse("");
        return ResponseEntity.ok(csv.getBytes());
    }
}
