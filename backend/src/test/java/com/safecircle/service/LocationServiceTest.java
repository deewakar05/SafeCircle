package com.safecircle.service;

import com.safecircle.dto.LocationDto.LocationUpdateRequest;
import com.safecircle.dto.LocationDto.LocationResponse;
import com.safecircle.model.Group;
import com.safecircle.model.Location;
import com.safecircle.model.User;
import com.safecircle.repository.GroupRepository;
import com.safecircle.repository.LocationRepository;
import com.safecircle.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocationService Unit Tests")
class LocationServiceTest {

    @Mock private LocationRepository    locationRepository;
    @Mock private GroupRepository       groupRepository;
    @Mock private UserRepository        userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks private LocationService locationService;

    private Group sampleGroup;
    private User  sampleUser;

    @BeforeEach
    void setUp() {
        sampleGroup = Group.builder()
                .id("group-1")
                .name("Test Group")
                .adminId("admin-1")
                .memberIds(new ArrayList<>(List.of("admin-1", "user-1")))
                .inviteCode("CODE01")
                .distanceThreshold(300)
                .build();

        sampleUser = User.builder()
                .id("user-1")
                .name("Alice")
                .email("alice@example.com")
                .role("MEMBER")
                .build();
    }

    // ── updateLocation: IDOR guard ───────────────────────────────────────────

    @Test
    @DisplayName("updateLocation: non-member user is rejected (IDOR protection)")
    void testUpdateLocation_notMember_throws() {
        LocationUpdateRequest req = new LocationUpdateRequest("group-1", 28.6, 77.2, "ONLINE", null);

        // Populate cache directly (group has no "attacker-99")
        when(groupRepository.findById("group-1")).thenReturn(Optional.of(sampleGroup));

        assertThatThrownBy(() -> locationService.updateLocation("attacker-99", req))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not a member");

        verify(locationRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    // ── updateLocation: happy path ───────────────────────────────────────────

    @Test
    @DisplayName("updateLocation: member location is saved and broadcast")
    void testUpdateLocation_member_success() {
        LocationUpdateRequest req = new LocationUpdateRequest("group-1", 28.6139, 77.2090, "ONLINE", 10.0);

        when(groupRepository.findById("group-1")).thenReturn(Optional.of(sampleGroup));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(sampleUser));
        when(locationRepository.findByUserIdAndGroupId("user-1", "group-1"))
                .thenReturn(Optional.empty()); // first update — create new record
        when(locationRepository.save(any(Location.class))).thenAnswer(inv -> inv.getArgument(0));
        when(locationRepository.findByGroupId("group-1")).thenReturn(List.of());

        LocationResponse res = locationService.updateLocation("user-1", req);

        assertThat(res.lat()).isEqualTo(28.6139);
        assertThat(res.lng()).isEqualTo(77.2090);
        assertThat(res.status()).isEqualTo("ONLINE");
        assertThat(res.userName()).isEqualTo("Alice");

        verify(locationRepository).save(any(Location.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/group/group-1"), any(LocationResponse.class));
    }

    // ── updateLocation: SOS broadcast ───────────────────────────────────────

    @Test
    @DisplayName("updateLocation: SOS status triggers alert broadcast")
    void testUpdateLocation_sos_broadcastsAlert() {
        LocationUpdateRequest req = new LocationUpdateRequest("group-1", 28.6, 77.2, "SOS", null);

        when(groupRepository.findById("group-1")).thenReturn(Optional.of(sampleGroup));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(sampleUser));
        when(locationRepository.findByUserIdAndGroupId("user-1", "group-1"))
                .thenReturn(Optional.empty());
        when(locationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(locationRepository.findByGroupId("group-1")).thenReturn(List.of());

        locationService.updateLocation("user-1", req);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/alerts/group-1"),
                contains("TRIGGERED SOS"));
    }

    // ── getGroupLocations: IDOR guard ────────────────────────────────────────

    @Test
    @DisplayName("getGroupLocations: non-member is rejected")
    void testGetGroupLocations_notMember_throws() {
        when(groupRepository.findById("group-1")).thenReturn(Optional.of(sampleGroup));

        assertThatThrownBy(() -> locationService.getGroupLocations("group-1", "outsider"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not a member");

        verify(locationRepository, never()).findByGroupId(anyString());
    }

    @Test
    @DisplayName("getGroupLocations: member receives list of locations")
    void testGetGroupLocations_member_success() {
        Location loc = Location.builder()
                .userId("user-1").groupId("group-1").userName("Alice")
                .lat(28.6).lng(77.2).status("ONLINE").timestamp(System.currentTimeMillis())
                .build();

        when(groupRepository.findById("group-1")).thenReturn(Optional.of(sampleGroup));
        when(locationRepository.findByGroupId("group-1")).thenReturn(List.of(loc));

        List<LocationResponse> result = locationService.getGroupLocations("group-1", "user-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo("user-1");
    }

    // ── markUserOffline ──────────────────────────────────────────────────────

    @Test
    @DisplayName("markUserOffline: broadcasts OFFLINE to every group user was in")
    void testMarkUserOffline_broadcastsToAllGroups() {
        Location loc1 = Location.builder()
                .userId("user-1").groupId("group-1").userName("Alice")
                .status("ONLINE").timestamp(System.currentTimeMillis())
                .lat(28.6).lng(77.2).build();
        Location loc2 = Location.builder()
                .userId("user-1").groupId("group-2").userName("Alice")
                .status("ONLINE").timestamp(System.currentTimeMillis())
                .lat(28.6).lng(77.2).build();

        when(locationRepository.findByUserId("user-1")).thenReturn(List.of(loc1, loc2));
        when(locationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        locationService.markUserOffline("user-1");

        verify(messagingTemplate).convertAndSend(eq("/topic/group/group-1"), any(LocationResponse.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/group/group-2"), any(LocationResponse.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/alerts/group-1"), anyString());
        verify(messagingTemplate).convertAndSend(eq("/topic/alerts/group-2"), anyString());
        assertThat(loc1.getStatus()).isEqualTo("OFFLINE");
        assertThat(loc2.getStatus()).isEqualTo("OFFLINE");
    }

    @Test
    @DisplayName("markUserOffline: already OFFLINE records are not re-saved")
    void testMarkUserOffline_skipAlreadyOffline() {
        Location loc = Location.builder()
                .userId("user-1").groupId("group-1").userName("Alice")
                .status("OFFLINE").timestamp(System.currentTimeMillis())
                .lat(0.0).lng(0.0).build();

        when(locationRepository.findByUserId("user-1")).thenReturn(List.of(loc));

        locationService.markUserOffline("user-1");

        verify(locationRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    // ── detectOfflineUsers: no findAll() ────────────────────────────────────

    @Test
    @DisplayName("detectOfflineUsers: uses direct stale query instead of groupRepository.findAll()")
    void testDetectOfflineUsers_noGroupFindAll() {
        Location stale = Location.builder()
                .userId("user-1").groupId("group-1").userName("Alice")
                .status("ONLINE").timestamp(1L) // very old
                .lat(28.6).lng(77.2).build();

        when(locationRepository.findByStatusNotAndTimestampLessThan(eq("OFFLINE"), anyLong()))
                .thenReturn(List.of(stale));
        when(locationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        locationService.detectOfflineUsers();

        // groupRepository should NEVER be called in the scheduler
        verify(groupRepository, never()).findAll();
        verify(locationRepository).findByStatusNotAndTimestampLessThan(eq("OFFLINE"), anyLong());
        verify(messagingTemplate).convertAndSend(
                eq("/topic/alerts/group-1"), contains("OFFLINE"));
        assertThat(stale.getStatus()).isEqualTo("OFFLINE");
    }
}
