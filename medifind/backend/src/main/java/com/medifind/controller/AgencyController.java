package com.medifind.controller;

import com.medifind.model.Agency;
import com.medifind.service.AgencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/agencies")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class AgencyController {

    private final AgencyService agencyService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Agency> create(@RequestBody Agency agency) {
        return ResponseEntity.ok(agencyService.createAgency(agency));
    }

    @GetMapping
    @PreAuthorize("hasRole('SHOPKEEPER') or hasRole('ADMIN')")
    public ResponseEntity<List<Agency>> list() {
        return ResponseEntity.ok(agencyService.getAllAgencies());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SHOPKEEPER') or hasRole('ADMIN')")
    public ResponseEntity<Agency> get(@PathVariable Long id) {
        return ResponseEntity.ok(agencyService.getAgency(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Agency> update(@PathVariable Long id, @RequestBody Agency agency) {
        return ResponseEntity.ok(agencyService.updateAgency(id, agency));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        agencyService.deleteAgency(id);
        return ResponseEntity.noContent().build();
    }
}
