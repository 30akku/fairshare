package com.anshi.fairshare.service;

import com.anshi.fairshare.api.ApiModels.ActivityResponse;
import com.anshi.fairshare.api.ApiModels.PageResponse;
import com.anshi.fairshare.domain.*;
import com.anshi.fairshare.repository.ActivityEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ActivityService {
    private final ActivityEventRepository events;
    private final GroupAccessService access;

    public ActivityService(ActivityEventRepository events, GroupAccessService access) { this.events = events; this.access = access; }

    public void record(ExpenseGroup group, AppUser actor, String eventType, String entityType, UUID entityId, String summary) {
        ActivityEvent event = new ActivityEvent();
        event.setGroup(group); event.setActor(actor); event.setEventType(eventType); event.setEntityType(entityType);
        event.setEntityId(entityId); event.setSummary(summary);
        events.save(event);
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> list(UUID groupId, UUID userId, int page, int size) {
        access.requireMember(groupId, userId);
        Page<ActivityEvent> result = events.findByGroupId(groupId, PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResponse<>(result.map(this::response).getContent(), result.hasNext() ? String.valueOf(page + 1) : null);
    }

    private ActivityResponse response(ActivityEvent event) {
        return new ActivityResponse(event.getId(), event.getActor() == null ? null : event.getActor().getId(),
                event.getActor() == null ? "FairShare" : event.getActor().getDisplayName(), event.getEventType(),
                event.getEntityType(), event.getEntityId(), event.getSummary(), event.getCreatedAt());
    }
}

