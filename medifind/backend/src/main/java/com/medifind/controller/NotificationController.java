package com.medifind.controller;

import com.medifind.model.Notification;
import com.medifind.model.Notification.NotificationType;
import com.medifind.model.User;
import com.medifind.repository.UserRepository;
import com.medifind.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasRole('SHOPKEEPER') or hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<List<Notification>> list(@RequestParam Long userId) {
        return ResponseEntity.ok(notificationService.getNotificationsForUser(userId));
    }

    @GetMapping("/unread")
    @PreAuthorize("hasRole('SHOPKEEPER') or hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<List<Notification>> unread(@RequestParam Long userId) {
        return ResponseEntity.ok(notificationService.getUnreadNotifications(userId));
    }

    @GetMapping("/count")
    @PreAuthorize("hasRole('SHOPKEEPER') or hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<Long> count(@RequestParam Long userId) {
        return ResponseEntity.ok(notificationService.getUnreadCount(userId));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasRole('SHOPKEEPER') or hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<Notification> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsRead(id));
    }

    @PatchMapping("/read-all")
    @PreAuthorize("hasRole('SHOPKEEPER') or hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<?> markAllRead(@RequestParam Long userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/send")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Notification> send(@RequestBody java.util.Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        String title = (String) body.get("title");
        String message = (String) body.get("message");
        NotificationType type = body.get("type") != null
                ? NotificationType.valueOf(body.get("type").toString())
                : NotificationType.SYSTEM;
        Long relatedId = body.get("relatedId") != null ? Long.valueOf(body.get("relatedId").toString()) : null;
        return ResponseEntity.ok(notificationService.createNotification(userId, title, message, type, relatedId));
    }

    @PostMapping("/send-bulk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Notification>> sendBulk(@RequestBody java.util.Map<String, Object> body) {
        String role = (String) body.get("role");
        String title = (String) body.get("title");
        String message = (String) body.get("message");
        NotificationType type = body.get("type") != null
                ? NotificationType.valueOf(body.get("type").toString())
                : NotificationType.SYSTEM;

        List<Notification> notifications = new java.util.ArrayList<>();
        for (User user : userRepository.findAll()) {
            if (role == null || user.getRole().name().equalsIgnoreCase(role)) {
                notifications.add(notificationService.createNotification(user.getId(), title, message, type, null));
            }
        }
        return ResponseEntity.ok(notifications);
    }
}
