package com.anshi.fairshare.service;

import com.anshi.fairshare.api.ApiException;
import com.anshi.fairshare.domain.AppUser;
import com.anshi.fairshare.domain.ExpenseGroup;
import com.anshi.fairshare.domain.GroupMember;
import com.anshi.fairshare.repository.AppUserRepository;
import com.anshi.fairshare.repository.ExpenseGroupRepository;
import com.anshi.fairshare.repository.GroupMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class GroupAccessService {
    private final ExpenseGroupRepository groups;
    private final GroupMemberRepository members;
    private final AppUserRepository users;

    public GroupAccessService(ExpenseGroupRepository groups, GroupMemberRepository members, AppUserRepository users) {
        this.groups = groups;
        this.members = members;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public ExpenseGroup requireMember(UUID groupId, UUID userId) {
        ExpenseGroup group = groups.findById(groupId).orElseThrow(() -> ApiException.notFound("Group"));
        if (!members.existsByGroupIdAndUserIdAndActiveTrue(groupId, userId)) throw ApiException.forbidden("You are not an active member of this group");
        return group;
    }

    @Transactional(readOnly = true)
    public ExpenseGroup requireOwner(UUID groupId, UUID userId) {
        ExpenseGroup group = requireMember(groupId, userId);
        if (!group.getOwner().getId().equals(userId)) throw ApiException.forbidden("Only the group owner can do this");
        return group;
    }

    @Transactional(readOnly = true)
    public Map<UUID, AppUser> requireActiveUsers(UUID groupId, Collection<UUID> userIds) {
        Set<UUID> requested = new HashSet<>(userIds);
        Map<UUID, AppUser> result = new HashMap<>();
        for (GroupMember member : members.findByGroupIdAndActiveTrueOrderByJoinedAt(groupId)) {
            if (requested.contains(member.getUser().getId())) result.put(member.getUser().getId(), member.getUser());
        }
        if (!result.keySet().containsAll(requested)) throw ApiException.badRequest("INVALID_GROUP_MEMBER", "Every payer and participant must be an active group member");
        return result;
    }
}

