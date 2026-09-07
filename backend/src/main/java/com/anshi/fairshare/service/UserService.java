package com.anshi.fairshare.service;

import com.anshi.fairshare.api.ApiException;
import com.anshi.fairshare.api.ApiModels.DeviceRequest;
import com.anshi.fairshare.api.ApiModels.ProfileUpdateRequest;
import com.anshi.fairshare.api.ApiModels.UserResponse;
import com.anshi.fairshare.domain.AppUser;
import com.anshi.fairshare.domain.DeviceRegistration;
import com.anshi.fairshare.domain.ExpenseGroup;
import com.anshi.fairshare.domain.GroupMember;
import com.anshi.fairshare.domain.DomainEnums.GroupRole;
import com.anshi.fairshare.repository.AppUserRepository;
import com.anshi.fairshare.repository.DeviceRegistrationRepository;
import com.anshi.fairshare.repository.ExpenseGroupRepository;
import com.anshi.fairshare.repository.GroupMemberRepository;
import com.anshi.fairshare.security.FirebaseTokenVerifier.VerifiedIdentity;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {
    private final AppUserRepository users;
    private final DeviceRegistrationRepository devices;
    private final ExpenseGroupRepository groups;
    private final GroupMemberRepository members;
    private final MediaService media;

    public UserService(AppUserRepository users, DeviceRegistrationRepository devices, ExpenseGroupRepository groups,
                       GroupMemberRepository members, MediaService media) {
        this.users = users;
        this.devices = devices;
        this.groups = groups;
        this.members = members;
        this.media = media;
    }

    @Transactional
    public AppUser provision(VerifiedIdentity identity) {
        return users.findByFirebaseUid(identity.uid()).map(existing -> {
            if (existing.isDeleted()) throw ApiException.forbidden("This FairShare account was deleted");
            if (!existing.getEmail().equalsIgnoreCase(identity.email())) existing.setEmail(identity.email().toLowerCase(Locale.ROOT));
            if (existing.getDisplayName() == null || existing.getDisplayName().isBlank()) existing.setDisplayName(identity.displayName());
            return existing;
        }).orElseGet(() -> {
            users.findByEmailIgnoreCase(identity.email()).ifPresent(conflict -> {
                throw ApiException.conflict("EMAIL_ALREADY_USED", "This email is linked to another sign-in identity");
            });
            AppUser user = new AppUser();
            user.setFirebaseUid(identity.uid());
            user.setEmail(identity.email().toLowerCase(Locale.ROOT));
            user.setDisplayName(identity.displayName().trim());
            return users.save(user);
        });
    }

    @Transactional(readOnly = true)
    public AppUser require(UUID id) {
        return users.findById(id).filter(user -> !user.isDeleted()).orElseThrow(() -> ApiException.notFound("User"));
    }

    public UserResponse response(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl(), user.getAvatarPublicId());
    }

    @Transactional
    public UserResponse update(UUID userId, ProfileUpdateRequest request) {
        AppUser user = require(userId);
        user.setDisplayName(request.displayName().trim());
        user.setAvatarUrl(blankToNull(request.avatarUrl()));
        user.setAvatarPublicId(blankToNull(request.avatarPublicId()));
        return response(user);
    }

    @Transactional
    public void registerDevice(UUID userId, DeviceRequest request) {
        AppUser user = require(userId);
        DeviceRegistration device = devices.findByToken(request.token()).orElseGet(DeviceRegistration::new);
        device.setUser(user);
        device.setToken(request.token());
        device.setPlatform(request.platform());
        devices.save(device);
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        AppUser user = require(userId);
        for (ExpenseGroup group : groups.findByOwnerIdAndArchivedFalse(userId)) {
            GroupMember successor = members.findByGroupIdAndActiveTrueOrderByJoinedAt(group.getId()).stream()
                    .filter(member -> !member.getUser().getId().equals(userId)).findFirst().orElse(null);
            if (successor == null) {
                group.setArchived(true);
            } else {
                group.setOwner(successor.getUser());
                successor.setRole(GroupRole.OWNER);
            }
        }
        for (GroupMember membership : members.findByUserIdAndActiveTrue(userId)) {
            membership.setActive(false);
            if (membership.getRole() == GroupRole.OWNER) membership.setRole(GroupRole.MEMBER);
        }
        devices.deleteByUserId(userId);
        String uid = user.getFirebaseUid();
        media.delete(user.getAvatarPublicId());
        user.setDeleted(true);
        user.setDisplayName("Deleted user");
        user.setEmail("deleted+" + user.getId() + "@fairshare.invalid");
        user.setAvatarUrl(null);
        user.setAvatarPublicId(null);
        try { FirebaseAuth.getInstance().deleteUser(uid); }
        catch (Exception ignored) { /* The database deletion remains authoritative; retrying Firebase deletion is safe. */ }
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
