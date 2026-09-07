package com.anshi.fairshare.security;

import com.anshi.fairshare.domain.AppUser;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String firebaseUid, String email, String displayName) {
    public static AuthenticatedUser from(AppUser user) {
        return new AuthenticatedUser(user.getId(), user.getFirebaseUid(), user.getEmail(), user.getDisplayName());
    }
}

