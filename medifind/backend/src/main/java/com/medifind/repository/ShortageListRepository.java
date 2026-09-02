package com.medifind.repository;

import com.medifind.model.ShortageList;
import com.medifind.model.ShortageList.ShortageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ShortageListRepository extends JpaRepository<ShortageList, Long> {
    List<ShortageList> findByShopId(Long shopId);
    List<ShortageList> findByShopIdAndStatus(Long shopId, ShortageStatus status);
    List<ShortageList> findByCustomerId(Long customerId);
}
