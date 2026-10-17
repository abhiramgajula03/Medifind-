package com.medifind.repository;

import com.medifind.model.BillScan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BillScanRepository extends JpaRepository<BillScan, Long> {
    List<BillScan> findByShopId(Long shopId);
}
