package com.medifind.repository;

import com.medifind.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByShopIdAndCustomerIdOrderByCreatedAtAsc(Long shopId, Long customerId);
    List<ChatMessage> findByShopIdAndIsReadFalseOrderByCreatedAtAsc(Long shopId);
    List<ChatMessage> findByCustomerIdAndIsReadFalseOrderByCreatedAtAsc(Long customerId);
}
