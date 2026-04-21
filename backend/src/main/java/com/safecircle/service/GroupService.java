package com.safecircle.service;

import com.safecircle.dto.GroupDto.*;
import com.safecircle.model.Group;
import com.safecircle.repository.GroupRepository;
import com.safecircle.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public GroupResponse createGroup(String adminId, CreateGroupRequest request) {
        String inviteCode = generateUniqueCode();
        Group group = Group.builder()
                .name(request.name())
                .adminId(adminId)
                .memberIds(new ArrayList<>(java.util.List.of(adminId)))
                .inviteCode(inviteCode)
                .distanceThreshold(request.distanceThreshold() > 0 ? request.distanceThreshold() : 300)
                .build();
        group = groupRepository.save(group);
        return toResponse(group);
    }

    public GroupResponse joinGroup(String userId, JoinGroupRequest request) {
        Group group = groupRepository.findByInviteCode(request.inviteCode())
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite code"));

        if (!group.getMemberIds().contains(userId)) {
            group.getMemberIds().add(userId);
            groupRepository.save(group);
        }
        return toResponse(group);
    }

    public GroupResponse getGroup(String groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        return toResponse(group);
    }

    public GroupResponse setThreshold(String groupId, String requesterId, SetThresholdRequest request) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        if (!group.getAdminId().equals(requesterId)) {
            throw new SecurityException("Only admin can set threshold");
        }
        group.setDistanceThreshold(request.threshold());
        return toResponse(groupRepository.save(group));
    }

    public void removeMember(String groupId, String adminId, String targetUserId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        if (!group.getAdminId().equals(adminId)) {
            throw new SecurityException("Only admin can remove members");
        }
        group.getMemberIds().remove(targetUserId);
        groupRepository.save(group);
    }

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
        return new GroupResponse(g.getId(), g.getName(), g.getAdminId(),
                g.getMemberIds(), g.getInviteCode(), g.getDistanceThreshold());
    }
}
