package com.medifind.controller;

import com.medifind.model.ChatMessage;
import com.medifind.model.ChatMessage.SenderType;
import com.medifind.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/history")
    @PreAuthorize("hasRole('SHOPKEEPER') or hasRole('CUSTOMER')")
    public ResponseEntity<List<ChatMessage>> history(@RequestParam Long shopId,
                                                     @RequestParam Long customerId) {
        return ResponseEntity.ok(chatService.getChatHistory(shopId, customerId));
    }

    @PostMapping("/send")
    @PreAuthorize("hasRole('SHOPKEEPER') or hasRole('CUSTOMER')")
    public ResponseEntity<ChatMessage> send(@RequestParam Long shopId,
                                            @RequestParam Long customerId,
                                            @RequestParam String customerName,
                                            @RequestParam SenderType senderType,
                                            @RequestBody String message) {
        return ResponseEntity.ok(chatService.sendMessage(shopId, customerId, customerName, senderType, message));
    }

    @GetMapping("/unread/shop")
    @PreAuthorize("hasRole('SHOPKEEPER')")
    public ResponseEntity<List<ChatMessage>> unreadForShop(@RequestParam Long shopId) {
        return ResponseEntity.ok(chatService.getUnreadMessages(shopId));
    }

    @GetMapping("/unread/customer")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<ChatMessage>> unreadForCustomer(@RequestParam Long customerId) {
        return ResponseEntity.ok(chatService.getUnreadMessagesForCustomer(customerId));
    }
}
