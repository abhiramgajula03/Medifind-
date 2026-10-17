package com.medifind.repository;

import com.medifind.model.Shop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShopRepository extends JpaRepository<Shop, Long> {
    List<Shop> findByIsOpenTrue();
    Optional<Shop> findByCreatedById(Long userId);
}
