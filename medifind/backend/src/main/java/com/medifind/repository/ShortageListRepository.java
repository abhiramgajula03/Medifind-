package com.medifind.repository;

import com.medifind.model.ShortageList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShortageListRepository extends JpaRepository<ShortageList, Long> {
    List<ShortageList> findByShopId(Long shopId);
    List<ShortageList> findByCustomerId(Long customerId);
}
