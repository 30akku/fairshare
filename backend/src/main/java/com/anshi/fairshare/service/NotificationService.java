package com.anshi.fairshare.service;

import com.anshi.fairshare.api.ApiException;
import com.anshi.fairshare.api.ApiModels.NotificationResponse;
import com.anshi.fairshare.api.ApiModels.PageResponse;
import com.anshi.fairshare.domain.*;
import com.anshi.fairshare.repository.AppNotificationRepository;
import com.anshi.fairshare.repository.DeviceRegistrationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class NotificationService {
    private final AppNotificationRepository notifications;
    private final DeviceRegistrationRepository devices;
    private final ObjectMapper objectMapper;

    public NotificationService(AppNotificationRepository notifications, DeviceRegistrationRepository devices, ObjectMapper objectMapper) {
        this.notifications = notifications; this.devices = devices; this.objectMapper = objectMapper;
    }

    @Transactional
    public void notifyUsers(Collection<AppUser> recipients, String type, String title, String body, Map<String, String> data) {
        if (recipients == null || recipients.isEmpty()) return;
        List<AppNotification> saved = new ArrayList<>();
        String json;
        try { json = objectMapper.writeValueAsString(data); } catch (Exception ignored) { json = "{}"; }
        for (AppUser user : recipients) {
            AppNotification notification = new AppNotification();
            notification.setUser(user); notification.setNotificationType(type); notification.setTitle(title);
            notification.setBody(body); notification.setDataJson(json);
            saved.add(notification);
        }
        notifications.saveAll(saved);
        push(recipients.stream().map(AppUser::getId).toList(), title, body, data);
    }

    @Async
    protected void push(List<UUID> userIds, String title, String body, Map<String, String> data) {
        try {
            List<String> tokens = devices.findByUserIdIn(userIds).stream().map(DeviceRegistration::getToken).distinct().toList();
            if (tokens.isEmpty()) return;
            for (int start = 0; start < tokens.size(); start += 500) {
                List<String> batch = tokens.subList(start, Math.min(start + 500, tokens.size()));
                MulticastMessage message = MulticastMessage.builder().addAllTokens(batch)
                        .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                        .putAllData(data).build();
                FirebaseMessaging.getInstance().sendEachForMulticast(message);
            }
        } catch (Exception ignored) { /* Push is best effort; the in-app notification is durable. */ }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID userId, int page, int size) {
        Page<AppNotification> result = notifications.findByUserId(userId,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResponse<>(result.map(this::response).getContent(), result.hasNext() ? String.valueOf(page + 1) : null);
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID notificationId) {
        AppNotification notification = notifications.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ApiException.notFound("Notification"));
        if (notification.getReadAt() == null) notification.setReadAt(Instant.now());
        return response(notification);
    }

    private NotificationResponse response(AppNotification notification) {
        Map<String, String> data;
        try { data = objectMapper.readValue(notification.getDataJson(), new TypeReference<>() {}); }
        catch (Exception ignored) { data = Map.of(); }
        return new NotificationResponse(notification.getId(), notification.getNotificationType(), notification.getTitle(),
                notification.getBody(), data, notification.getReadAt(), notification.getCreatedAt());
    }
}

