package com.medifind.service;

import com.medifind.model.ChatMessage;
import com.medifind.model.ChatMessage.SenderType;
import com.medifind.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;

    public List<ChatMessage> getChatHistory(Long shopId, Long customerId) {
        return chatMessageRepository.findByShopIdAndCustomerIdOrderByCreatedAtAsc(shopId, customerId);
    }

    public ChatMessage sendMessage(Long shopId, Long customerId, String customerName, SenderType senderType, String message) {
        ChatMessage chat = ChatMessage.builder()
                .shopId(shopId)
                .customerId(customerId)
                .customerName(customerName)
                .senderType(senderType)
                .message(message)
                .isRead(false)
                .build();
        return chatMessageRepository.save(chat);
    }

    public List<ChatMessage> getUnreadMessages(Long shopId) {
        return chatMessageRepository.findByShopIdAndIsReadFalseOrderByCreatedAtAsc(shopId);
    }

    public List<ChatMessage> getUnreadMessagesForCustomer(Long customerId) {
        return chatMessageRepository.findByCustomerIdAndIsReadFalseOrderByCreatedAtAsc(customerId);
    }
}
