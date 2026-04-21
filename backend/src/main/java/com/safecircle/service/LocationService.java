package com.safecircle.service;

import com.safecircle.dto.LocationDto.*;
import com.safecircle.model.Location;
import com.safecircle.repository.GroupRepository;
import com.safecircle.repository.LocationRepository;
import com.safecircle.repository.UserRepository;
import com.safecircle.util.HaversineUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private static final long OFFLINE_THRESHOLD_MS = 30_000; // 30s without update = offline

    /**
     * REST: update location, persist, then broadcast via WebSocket.
     */
    public LocationResponse updateLocation(String userId, LocationUpdateRequest request) {
        String userName = userRepository.findById(userId)
                .map(u -> u.getName()).orElse("Unknown");

        Location location = locationRepository
                .findByUserIdAndGroupId(userId, request.groupId())
                .orElse(Location.builder().userId(userId).groupId(request.groupId()).build());

        location.setLat(request.lat());
        location.setLng(request.lng());
        location.setStatus(request.status() != null ? request.status() : "ONLINE");
        location.setTimestamp(System.currentTimeMillis());
        location.setUserName(userName);

        locationRepository.save(location);

        LocationResponse response = toResponse(location);

        // Broadcast to all group members via WebSocket
        messagingTemplate.convertAndSend("/topic/group/" + request.groupId(), response);

        // Run distance check for alerts
        checkDistanceAlerts(request.groupId(), location);

        return response;
    }

    /**
     * REST: get all member locations for a group.
     */
    public List<LocationResponse> getGroupLocations(String groupId) {
        return locationRepository.findByGroupId(groupId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * Scheduled: mark members OFFLINE if no update in OFFLINE_THRESHOLD_MS.
     */
    @Scheduled(fixedRate = 15_000)
    public void detectOfflineUsers() {
        long threshold = System.currentTimeMillis() - OFFLINE_THRESHOLD_MS;
        // Get all groups (simplified - in production paginate)
        groupRepository.findAll().forEach(group -> {
            List<Location> stale = locationRepository
                    .findByGroupIdAndTimestampLessThan(group.getId(), threshold);
            stale.forEach(loc -> {
                if (!"OFFLINE".equals(loc.getStatus())) {
                    loc.setStatus("OFFLINE");
                    locationRepository.save(loc);
                    // Alert the group
                    String alertMsg = "⚠️ " + loc.getUserName() + " is OFFLINE";
                    messagingTemplate.convertAndSend("/topic/alerts/" + group.getId(), alertMsg);
                    log.info("Offline alert sent for user {} in group {}", loc.getUserId(), group.getId());
                }
            });
        });
    }

    /**
     * Check if any member has exceeded the group distance threshold.
     */
    private void checkDistanceAlerts(String groupId, Location updatedLocation) {
        groupRepository.findById(groupId).ifPresent(group -> {
            double threshold = group.getDistanceThreshold();
            List<Location> allLocations = locationRepository.findByGroupId(groupId);

            // Find the admin's location as the reference point
            allLocations.stream()
                    .filter(loc -> loc.getUserId().equals(group.getAdminId()))
                    .findFirst()
                    .ifPresent(adminLoc -> {
                        allLocations.forEach(memberLoc -> {
                            if (!memberLoc.getUserId().equals(group.getAdminId())) {
                                double dist = HaversineUtil.distanceInMetres(
                                        adminLoc.getLat(), adminLoc.getLng(),
                                        memberLoc.getLat(), memberLoc.getLng()
                                );
                                if (dist > threshold) {
                                    String alertMsg = String.format("📍 %s is %.0fm behind the group",
                                            memberLoc.getUserName(), dist);
                                    messagingTemplate.convertAndSend("/topic/alerts/" + groupId, alertMsg);
                                }
                            }
                        });
                    });
        });
    }

    private LocationResponse toResponse(Location l) {
        return new LocationResponse(l.getUserId(), l.getUserName(), l.getGroupId(),
                l.getLat(), l.getLng(), l.getStatus(), l.getTimestamp());
    }
}
