package com.medifind.repository;

import com.medifind.model.AgencyBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AgencyBillRepository extends JpaRepository<AgencyBill, Long> {
    List<AgencyBill> findByShopId(Long shopId);
}
