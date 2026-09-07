package com.anshi.fairshare.service;

import com.anshi.fairshare.api.ApiException;
import com.anshi.fairshare.api.ApiModels.*;
import com.anshi.fairshare.config.FairShareProperties;
import com.anshi.fairshare.domain.*;
import com.anshi.fairshare.domain.DomainEnums.GroupRole;
import com.anshi.fairshare.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class GroupService {
    private final ExpenseGroupRepository groups;
    private final GroupMemberRepository members;
    private final InvitationRepository invitations;
    private final UserService userService;
    private final GroupAccessService access;
    private final ActivityService activity;
    private final NotificationService notificationService;
    private final FairShareProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public GroupService(ExpenseGroupRepository groups, GroupMemberRepository members, InvitationRepository invitations,
                        UserService userService, GroupAccessService access, ActivityService activity,
                        NotificationService notificationService, FairShareProperties properties) {
        this.groups = groups; this.members = members; this.invitations = invitations; this.userService = userService;
        this.access = access; this.activity = activity; this.notificationService = notificationService; this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> list(UUID userId) { return groups.findActiveForUser(userId).stream().map(this::response).toList(); }

    @Transactional
    public GroupResponse create(UUID userId, GroupCreateRequest request) {
        AppUser user = userService.require(userId);
        ExpenseGroup group = new ExpenseGroup();
        group.setName(request.name().trim()); group.setDescription(blankToNull(request.description())); group.setOwner(user);
        groups.save(group);
        GroupMember member = new GroupMember();
        member.setGroup(group); member.setUser(user); member.setRole(GroupRole.OWNER); member.setActive(true);
        members.save(member);
        activity.record(group, user, "GROUP_CREATED", "GROUP", group.getId(), user.getDisplayName() + " created the group");
        return response(group);
    }

    @Transactional(readOnly = true)
    public GroupResponse get(UUID groupId, UUID userId) { return response(access.requireMember(groupId, userId)); }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(UUID groupId, UUID userId) {
        access.requireMember(groupId, userId);
        return members.findByGroupIdAndActiveTrueOrderByJoinedAt(groupId).stream().map(member -> new MemberResponse(
                member.getUser().getId(), member.getUser().getDisplayName(), member.getUser().getEmail(),
                member.getUser().getAvatarUrl(), member.getRole().name(), member.getJoinedAt())).toList();
    }

    @Transactional
    public InviteResponse createInvite(UUID groupId, UUID userId) {
        ExpenseGroup group = access.requireOwner(groupId, userId);
        AppUser actor = userService.require(userId);
        byte[] random = new byte[32]; secureRandom.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        Invitation invitation = new Invitation();
        invitation.setGroup(group); invitation.setCreatedBy(actor); invitation.setTokenHash(hash(token));
        invitation.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS)); invitation.setMaxUses(25);
        invitations.save(invitation);
        activity.record(group, actor, "INVITE_CREATED", "INVITATION", invitation.getId(), actor.getDisplayName() + " created an invite link");
        return inviteResponse(invitation, token);
    }

    @Transactional
    public GroupResponse acceptInvite(String token, UUID userId) {
        Invitation invitation = invitations.findByTokenHash(hash(token)).orElseThrow(() -> ApiException.notFound("Invitation"));
        if (invitation.isRevoked() || invitation.getExpiresAt().isBefore(Instant.now()) || invitation.getUseCount() >= invitation.getMaxUses()) {
            throw ApiException.badRequest("INVITATION_EXPIRED", "This invitation is expired, revoked, or fully used");
        }
        AppUser user = userService.require(userId);
        GroupMember membership = members.findByGroupIdAndUserId(invitation.getGroup().getId(), userId).orElseGet(GroupMember::new);
        if (membership.getId() == null) {
            membership.setGroup(invitation.getGroup()); membership.setUser(user); membership.setRole(GroupRole.MEMBER);
        }
        if (!membership.isActive()) membership.setActive(true);
        else if (membership.getId() != null) return response(invitation.getGroup());
        members.save(membership);
        invitation.setUseCount(invitation.getUseCount() + 1);
        activity.record(invitation.getGroup(), user, "MEMBER_JOINED", "MEMBERSHIP", membership.getId(), user.getDisplayName() + " joined the group");
        List<AppUser> recipients = members.findByGroupIdAndActiveTrueOrderByJoinedAt(invitation.getGroup().getId()).stream()
                .map(GroupMember::getUser).filter(candidate -> !candidate.getId().equals(userId)).toList();
        notificationService.notifyUsers(recipients, "MEMBER_JOINED", "New group member", user.getDisplayName() + " joined " + invitation.getGroup().getName(),
                Map.of("groupId", invitation.getGroup().getId().toString()));
        return response(invitation.getGroup());
    }

    @Transactional
    public void revokeInvite(UUID groupId, UUID inviteId, UUID userId) {
        access.requireOwner(groupId, userId);
        Invitation invitation = invitations.findById(inviteId).filter(value -> value.getGroup().getId().equals(groupId))
                .orElseThrow(() -> ApiException.notFound("Invitation"));
        invitation.setRevoked(true);
    }

    @Transactional
    public GroupResponse archive(UUID groupId, UUID userId) {
        ExpenseGroup group = access.requireOwner(groupId, userId);
        group.setArchived(true);
        activity.record(group, userService.require(userId), "GROUP_ARCHIVED", "GROUP", groupId, "The group was archived");
        return response(group);
    }

    private GroupResponse response(ExpenseGroup group) {
        return new GroupResponse(group.getId(), group.getName(), group.getDescription(), group.getOwner().getId(),
                group.isArchived(), group.getVersion(), group.getCreatedAt());
    }

    private InviteResponse inviteResponse(Invitation invitation, String token) {
        String base = properties.publicBaseUrl().replaceAll("/$", "");
        return new InviteResponse(invitation.getId(), invitation.getGroup().getId(), token, base + "/invite/?token=" + token,
                invitation.getExpiresAt(), invitation.getMaxUses(), invitation.getUseCount(), invitation.isRevoked());
    }

    private String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

