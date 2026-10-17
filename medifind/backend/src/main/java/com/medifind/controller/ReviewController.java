package com.medifind.controller;

import com.medifind.model.Review;
import com.medifind.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<Review> create(@RequestBody java.util.Map<String, Object> body) {
        Long shopId = Long.valueOf(body.get("shopId").toString());
        Long customerId = Long.valueOf(body.get("customerId").toString());
        String customerName = (String) body.get("customerName");
        Integer rating = Integer.valueOf(body.get("rating").toString());
        String comment = (String) body.get("comment");
        return ResponseEntity.ok(reviewService.createReview(shopId, customerId, customerName, rating, comment));
    }

    @GetMapping("/shop/{shopId}")
    public ResponseEntity<List<Review>> getByShop(@PathVariable Long shopId) {
        return ResponseEntity.ok(reviewService.getReviewsForShop(shopId));
    }

    @GetMapping("/avg/shop/{shopId}")
    public ResponseEntity<Double> avgRating(@PathVariable Long shopId) {
        return ResponseEntity.ok(reviewService.getAverageRating(shopId));
    }
}
