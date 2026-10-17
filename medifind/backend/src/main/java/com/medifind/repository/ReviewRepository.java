package com.medifind.repository;

import com.medifind.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByShopIdOrderByCreatedAtDesc(Long shopId);
    Double findByShopId(Long shopId);
}
