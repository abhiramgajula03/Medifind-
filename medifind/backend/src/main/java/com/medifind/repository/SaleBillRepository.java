package com.medifind.repository;

import com.medifind.model.SaleBill;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface SaleBillRepository extends JpaRepository<SaleBill, Long> {
    List<SaleBill> findByShopIdAndBilledAtBetweenOrderByBilledAtDesc(Long shopId, LocalDateTime start, LocalDateTime end);
}
