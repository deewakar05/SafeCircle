package com.safecircle.controller;

import com.safecircle.dto.GroupDto.*;
import com.safecircle.repository.UserRepository;
import com.safecircle.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;
    private final UserRepository userRepository;

    @Autowired
    public GroupController(GroupService groupService, UserRepository userRepository) {
        this.groupService = groupService;
        this.userRepository = userRepository;
    }

    @PostMapping("/create")
    public ResponseEntity<GroupResponse> createGroup(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateGroupRequest request) {
        return ResponseEntity.ok(groupService.createGroup(getUserId(userDetails), request));
    }

    @PostMapping("/join")
    public ResponseEntity<GroupResponse> joinGroup(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody JoinGroupRequest request) {
        return ResponseEntity.ok(groupService.joinGroup(getUserId(userDetails), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupResponse> getGroup(@PathVariable String id) {
        return ResponseEntity.ok(groupService.getGroup(id));
    }

    @PutMapping("/{id}/threshold")
    public ResponseEntity<GroupResponse> setThreshold(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SetThresholdRequest request) {
        return ResponseEntity.ok(groupService.setThreshold(id, getUserId(userDetails), request));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable String id,
            @PathVariable String userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        groupService.removeMember(id, getUserId(userDetails), userId);
        return ResponseEntity.noContent().build();
    }

    private String getUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"))
                .getId();
    }
}
