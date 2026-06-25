package com.medifind.repository;

import com.medifind.model.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {
    Optional<Shop> findByCreatedById(Long userId);

    // Find all shops within radius km using Haversine formula in SQL
    @Query(value = """
        SELECT *, 
            (6371 * acos(
                cos(radians(:lat)) * cos(radians(lat)) *
                cos(radians(lng) - radians(:lng)) +
                sin(radians(:lat)) * sin(radians(lat))
            )) AS distance
        FROM shops
        HAVING distance <= :radiusKm
        ORDER BY distance ASC
        """, nativeQuery = true)
    List<Shop> findNearbyShops(@Param("lat") double lat,
                               @Param("lng") double lng,
                               @Param("radiusKm") double radiusKm);

    // Find only OPEN shops within radius
    @Query(value = """
        SELECT *, 
            (6371 * acos(
                cos(radians(:lat)) * cos(radians(lat)) *
                cos(radians(lng) - radians(:lng)) +
                sin(radians(:lat)) * sin(radians(lat))
            )) AS distance
        FROM shops
        WHERE is_open = true
        HAVING distance <= :radiusKm
        ORDER BY distance ASC
        """, nativeQuery = true)
    List<Shop> findNearbyOpenShops(@Param("lat") double lat,
                                   @Param("lng") double lng,
                                   @Param("radiusKm") double radiusKm);

    // Find 24hr shops within radius
    @Query(value = """
        SELECT *, 
            (6371 * acos(
                cos(radians(:lat)) * cos(radians(lat)) *
                cos(radians(lng) - radians(:lng)) +
                sin(radians(:lat)) * sin(radians(lat))
            )) AS distance
        FROM shops
        WHERE is_24hr = true
        HAVING distance <= :radiusKm
        ORDER BY distance ASC
        """, nativeQuery = true)
    List<Shop> findNearby24hrShops(@Param("lat") double lat,
                                   @Param("lng") double lng,
                                   @Param("radiusKm") double radiusKm);
}
