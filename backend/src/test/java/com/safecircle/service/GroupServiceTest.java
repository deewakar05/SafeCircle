package com.safecircle.service;

import com.safecircle.dto.GroupDto.*;
import com.safecircle.model.Group;
import com.safecircle.repository.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupService Unit Tests")
class GroupServiceTest {

    @Mock private GroupRepository  groupRepository;
    @Mock private LocationService  locationService;

    @InjectMocks private GroupService groupService;

    private Group sampleGroup;

    @BeforeEach
    void setUp() {
        sampleGroup = Group.builder()
                .id("group-1")
                .name("Trip Goa 2025")
                .adminId("admin-1")
                .memberIds(new ArrayList<>(List.of("admin-1", "member-2")))
                .inviteCode("GOATRP")
                .distanceThreshold(300)
                .build();
    }

    // ── createGroup ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("createGroup: saves group with admin as first member and unique invite code")
    void testCreateGroup_success() {
        CreateGroupRequest req = new CreateGroupRequest("Trip Goa 2025", 500);

        when(groupRepository.existsByInviteCode(anyString())).thenReturn(false);
        when(groupRepository.save(any(Group.class))).thenReturn(sampleGroup);

        GroupResponse res = groupService.createGroup("admin-1", req);

        assertThat(res.name()).isEqualTo("Trip Goa 2025");
        assertThat(res.adminId()).isEqualTo("admin-1");
        assertThat(res.memberIds()).contains("admin-1");
        verify(groupRepository).save(any(Group.class));
    }

    @Test
    @DisplayName("createGroup: default threshold 300m when 0 supplied")
    void testCreateGroup_defaultThreshold() {
        CreateGroupRequest req = new CreateGroupRequest("Group", 0);

        when(groupRepository.existsByInviteCode(anyString())).thenReturn(false);
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> {
            Group g = inv.getArgument(0);
            assertThat(g.getDistanceThreshold()).isEqualTo(300);
            return sampleGroup;
        });

        groupService.createGroup("admin-1", req);
        verify(groupRepository).save(any(Group.class));
    }

    // ── joinGroup ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("joinGroup: valid code adds user to memberIds")
    void testJoinGroup_validCode() {
        JoinGroupRequest req = new JoinGroupRequest("GOATRP");
        sampleGroup.getMemberIds().clear();
        sampleGroup.getMemberIds().add("admin-1"); // no member-2 yet

        when(groupRepository.findByInviteCode("GOATRP")).thenReturn(Optional.of(sampleGroup));
        when(groupRepository.save(any(Group.class))).thenReturn(sampleGroup);

        GroupResponse res = groupService.joinGroup("member-2", req);

        assertThat(res.memberIds()).contains("member-2");
        verify(groupRepository).save(any(Group.class));
        verify(locationService).evictGroupCache("group-1");
    }

    @Test
    @DisplayName("joinGroup: invalid invite code throws IllegalArgumentException")
    void testJoinGroup_invalidCode() {
        JoinGroupRequest req = new JoinGroupRequest("BADCOD");

        when(groupRepository.findByInviteCode("BADCOD")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.joinGroup("user-1", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid invite code");

        verify(groupRepository, never()).save(any());
    }

    @Test
    @DisplayName("joinGroup: joining same group twice is idempotent")
    void testJoinGroup_alreadyMember() {
        JoinGroupRequest req = new JoinGroupRequest("GOATRP");

        when(groupRepository.findByInviteCode("GOATRP")).thenReturn(Optional.of(sampleGroup));

        groupService.joinGroup("admin-1", req); // admin-1 is already in memberIds

        verify(groupRepository, never()).save(any()); // no re-save needed
    }

    // ── getGroup (IDOR) ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getGroup: member receives group details")
    void testGetGroup_member_success() {
        when(groupRepository.findById("group-1")).thenReturn(Optional.of(sampleGroup));

        GroupResponse res = groupService.getGroup("group-1", "member-2");

        assertThat(res.id()).isEqualTo("group-1");
    }

    @Test
    @DisplayName("getGroup: non-member receives SecurityException (IDOR protection)")
    void testGetGroup_notMember_throwsSecurityException() {
        when(groupRepository.findById("group-1")).thenReturn(Optional.of(sampleGroup));

        assertThatThrownBy(() -> groupService.getGroup("group-1", "attacker-99"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not a member");
    }

    @Test
    @DisplayName("getGroup: non-existent group throws IllegalArgumentException")
    void testGetGroup_notFound() {
        when(groupRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.getGroup("ghost", "anyone"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ── setThreshold ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("setThreshold: non-admin throws SecurityException")
    void testSetThreshold_nonAdmin_throws() {
        SetThresholdRequest req = new SetThresholdRequest(500.0);

        when(groupRepository.findById("group-1")).thenReturn(Optional.of(sampleGroup));

        assertThatThrownBy(() -> groupService.setThreshold("group-1", "member-2", req))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("admin");
    }

    @Test
    @DisplayName("setThreshold: admin succeeds and cache is evicted")
    void testSetThreshold_admin_success() {
        SetThresholdRequest req = new SetThresholdRequest(500.0);

        when(groupRepository.findById("group-1")).thenReturn(Optional.of(sampleGroup));
        when(groupRepository.save(any())).thenReturn(sampleGroup);

        groupService.setThreshold("group-1", "admin-1", req);

        assertThat(sampleGroup.getDistanceThreshold()).isEqualTo(500.0);
        verify(locationService).evictGroupCache("group-1");
    }

    // ── removeMember ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("removeMember: non-admin throws SecurityException")
    void testRemoveMember_nonAdmin_throws() {
        when(groupRepository.findById("group-1")).thenReturn(Optional.of(sampleGroup));

        assertThatThrownBy(() -> groupService.removeMember("group-1", "member-2", "member-2"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("removeMember: admin can remove a member and cache is evicted")
    void testRemoveMember_admin_success() {
        when(groupRepository.findById("group-1")).thenReturn(Optional.of(sampleGroup));
        when(groupRepository.save(any())).thenReturn(sampleGroup);

        groupService.removeMember("group-1", "admin-1", "member-2");

        assertThat(sampleGroup.getMemberIds()).doesNotContain("member-2");
        verify(locationService).evictGroupCache("group-1");
    }
}
