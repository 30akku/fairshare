package com.anshi.fairshare.api;

import com.anshi.fairshare.api.ApiModels.*;
import com.anshi.fairshare.security.AuthenticatedUser;
import com.anshi.fairshare.service.MediaService;
import com.anshi.fairshare.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class UserController {
    private final UserService users;
    private final MediaService media;
    public UserController(UserService users, MediaService media) { this.users = users; this.media = media; }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) { return users.response(users.require(principal.id())); }

    @PatchMapping("/me")
    UserResponse update(@AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody ProfileUpdateRequest request) {
        return users.update(principal.id(), request);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AuthenticatedUser principal) { users.deleteAccount(principal.id()); }

    @PutMapping("/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void device(@AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody DeviceRequest request) {
        users.registerDevice(principal.id(), request);
    }

    @PostMapping("/media/signature")
    MediaSignatureResponse mediaSignature(@AuthenticationPrincipal AuthenticatedUser principal) { return media.signature(principal.id()); }
}

