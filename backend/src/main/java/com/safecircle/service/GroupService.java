package com.safecircle.service;

import com.safecircle.dto.GroupDto.*;
import com.safecircle.dto.RouteDto.*;
import com.safecircle.model.Group;
import com.safecircle.repository.GroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

@Service
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);

    private final GroupRepository groupRepository;
    private final LocationService locationService; // used only for cache eviction

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    public GroupService(GroupRepository groupRepository, LocationService locationService) {
        this.groupRepository = groupRepository;
        this.locationService = locationService;
    }

    public GroupResponse createGroup(String adminId, CreateGroupRequest request) {
        String inviteCode = generateUniqueCode();
        List<String> members = new ArrayList<>();
        members.add(adminId);
        Group group = Group.builder()
                .name(request.name())
                .adminId(adminId)
                .memberIds(members)
                .inviteCode(inviteCode)
                .distanceThreshold(request.distanceThreshold() > 0 ? request.distanceThreshold() : 300)
                .build();
        group = groupRepository.save(group);
        log.info("[Group] Created group '{}' ({}) by user {}", group.getName(), group.getId(), adminId);
        return toResponse(group);
    }

    public GroupResponse joinGroup(String userId, JoinGroupRequest request) {
        Group group = groupRepository.findByInviteCode(request.inviteCode())
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite code"));
        if (!group.getMemberIds().contains(userId)) {
            group.getMemberIds().add(userId);
            groupRepository.save(group);
            locationService.evictGroupCache(group.getId()); // membership changed
            log.info("[Group] User {} joined group '{}' ({})", userId, group.getName(), group.getId());
        }
        return toResponse(group);
    }

    /**
     * Fetch group details.
     *
     * <h3>Security (IDOR fix)</h3>
     * Only members of the group may read its details (name, invite code, member list).
     * Non-members receive a 403 via the {@link SecurityException} handler.
     */
    public GroupResponse getGroup(String groupId, String requestingUserId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        if (!group.getMemberIds().contains(requestingUserId)) {
            log.warn("[SECURITY] User {} attempted to access group {} (not a member)",
                    requestingUserId, groupId);
            throw new SecurityException("Access denied: you are not a member of this group");
        }
        return toResponse(group);
    }

    public List<GroupResponse> getUserGroups(String userId) {
        return groupRepository.findByMemberIdsContaining(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public GroupResponse setThreshold(String groupId, String requesterId, SetThresholdRequest request) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        if (!group.getAdminId().equals(requesterId)) {
            throw new SecurityException("Only the group admin can set the distance threshold");
        }
        group.setDistanceThreshold(request.threshold());
        Group saved = groupRepository.save(group);
        locationService.evictGroupCache(groupId); // threshold changed
        log.info("[Group] Admin {} set threshold={}m for group {}", requesterId, request.threshold(), groupId);
        return toResponse(saved);
    }

    public void removeMember(String groupId, String adminId, String targetUserId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        if (!group.getAdminId().equals(adminId)) {
            throw new SecurityException("Only the group admin can remove members");
        }
        group.getMemberIds().remove(targetUserId);
        groupRepository.save(group);
        locationService.evictGroupCache(groupId); // membership changed
        log.info("[Group] Admin {} removed user {} from group {}", adminId, targetUserId, groupId);
    }

    public GroupResponse updateRoute(String groupId, String adminId, UpdateRouteRequest request) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        if (!group.getAdminId().equals(adminId)) {
            throw new SecurityException("Only the group admin can update the route");
        }
        List<Group.Checkpoint> checkpoints = request.checkpoints().stream()
                .map(dto -> new Group.Checkpoint(dto.lat(), dto.lng(), dto.name()))
                .toList();
        group.setRoute(checkpoints);
        Group saved = groupRepository.save(group);
        log.info("[Group] Admin {} updated route for group {} ({} checkpoints)",
                adminId, groupId, checkpoints.size());
        return toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────

    private String generateUniqueCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
            }
            code = sb.toString();
        } while (groupRepository.existsByInviteCode(code));
        return code;
    }

    private GroupResponse toResponse(Group g) {
        return new GroupResponse(
                g.getId(), g.getName(), g.getAdminId(),
                g.getMemberIds(), g.getInviteCode(), g.getDistanceThreshold());
    }
}
