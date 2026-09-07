package com.anshi.fairshare.api;

import com.anshi.fairshare.api.ApiModels.*;
import com.anshi.fairshare.security.AuthenticatedUser;
import com.anshi.fairshare.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class GroupController {
    private final GroupService groups;
    public GroupController(GroupService groups) { this.groups = groups; }

    @GetMapping("/groups")
    List<GroupResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) { return groups.list(principal.id()); }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    GroupResponse create(@AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody GroupCreateRequest request) {
        return groups.create(principal.id(), request);
    }

    @GetMapping("/groups/{groupId}")
    GroupResponse get(@PathVariable UUID groupId, @AuthenticationPrincipal AuthenticatedUser principal) { return groups.get(groupId, principal.id()); }

    @GetMapping("/groups/{groupId}/members")
    List<MemberResponse> members(@PathVariable UUID groupId, @AuthenticationPrincipal AuthenticatedUser principal) {
        return groups.listMembers(groupId, principal.id());
    }

    @PostMapping("/groups/{groupId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    InviteResponse invite(@PathVariable UUID groupId, @AuthenticationPrincipal AuthenticatedUser principal) {
        return groups.createInvite(groupId, principal.id());
    }

    @DeleteMapping("/groups/{groupId}/invites/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable UUID groupId, @PathVariable UUID inviteId, @AuthenticationPrincipal AuthenticatedUser principal) {
        groups.revokeInvite(groupId, inviteId, principal.id());
    }

    @PostMapping("/invites/{token}/accept")
    GroupResponse accept(@PathVariable String token, @AuthenticationPrincipal AuthenticatedUser principal) {
        return groups.acceptInvite(token, principal.id());
    }

    @PostMapping("/groups/{groupId}/archive")
    GroupResponse archive(@PathVariable UUID groupId, @AuthenticationPrincipal AuthenticatedUser principal) {
        return groups.archive(groupId, principal.id());
    }
}

