package com.anshi.fairshare.api;

import com.anshi.fairshare.api.ApiModels.NotificationResponse;
import com.anshi.fairshare.api.ApiModels.PageResponse;
import com.anshi.fairshare.security.AuthenticatedUser;
import com.anshi.fairshare.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications) { this.notifications = notifications; }

    @GetMapping
    PageResponse<NotificationResponse> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "30") int size) {
        return notifications.list(principal.id(), Math.max(page, 0), Math.max(size, 1));
    }

    @PostMapping("/{notificationId}/read")
    NotificationResponse markRead(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID notificationId) {
        return notifications.markRead(principal.id(), notificationId);
    }
}

