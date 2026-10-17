package com.medifind.service;

import com.medifind.model.Review;
import com.medifind.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;

    public Review createReview(Long shopId, Long customerId, String customerName, Integer rating, String comment) {
        return reviewRepository.save(Review.builder()
                .shopId(shopId)
                .customerId(customerId)
                .customerName(customerName)
                .rating(rating)
                .comment(comment)
                .build());
    }

    public List<Review> getReviewsForShop(Long shopId) {
        return reviewRepository.findByShopIdOrderByCreatedAtDesc(shopId);
    }

    public Double getAverageRating(Long shopId) {
        Double avg = reviewRepository.findByShopId(shopId);
        return avg != null ? avg : 0.0;
    }
}
