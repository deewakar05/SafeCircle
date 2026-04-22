package com.safecircle.service;

import com.safecircle.dto.LocationDto.*;
import com.safecircle.model.Location;
import com.safecircle.repository.GroupRepository;
import com.safecircle.repository.LocationRepository;
import com.safecircle.repository.UserRepository;
import com.safecircle.util.HaversineUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);
    private static final long OFFLINE_THRESHOLD_MS = 30_000;

    private final LocationRepository locationRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public LocationService(LocationRepository locationRepository,
                           GroupRepository groupRepository,
                           UserRepository userRepository,
                           SimpMessagingTemplate messagingTemplate) {
        this.locationRepository = locationRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

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
        location.setAccuracy(request.accuracy());

        locationRepository.save(location);
        LocationResponse response = toResponse(location);
        messagingTemplate.convertAndSend("/topic/group/" + request.groupId(), response);
        checkDistanceAlerts(request.groupId(), location);
        return response;
    }

    public List<LocationResponse> getGroupLocations(String groupId) {
        return locationRepository.findByGroupId(groupId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Scheduled(fixedRate = 15_000)
    public void detectOfflineUsers() {
        long threshold = System.currentTimeMillis() - OFFLINE_THRESHOLD_MS;
        groupRepository.findAll().forEach(group -> {
            List<Location> stale = locationRepository
                    .findByGroupIdAndTimestampLessThan(group.getId(), threshold);
            stale.forEach(loc -> {
                if (!"OFFLINE".equals(loc.getStatus())) {
                    loc.setStatus("OFFLINE");
                    locationRepository.save(loc);
                    String alertMsg = "⚠️ " + loc.getUserName() + " is OFFLINE";
                    messagingTemplate.convertAndSend("/topic/alerts/" + group.getId(), alertMsg);
                    log.info("Offline alert sent for user {} in group {}", loc.getUserId(), group.getId());
                }
            });
        });
    }

    private void checkDistanceAlerts(String groupId, Location updatedLocation) {
        groupRepository.findById(groupId).ifPresent(group -> {
            double threshold = group.getDistanceThreshold();
            List<Location> allLocations = locationRepository.findByGroupId(groupId);
            allLocations.stream()
                    .filter(loc -> loc.getUserId().equals(group.getAdminId()))
                    .findFirst()
                    .ifPresent(adminLoc -> allLocations.forEach(memberLoc -> {
                        if (!memberLoc.getUserId().equals(group.getAdminId())) {
                            double dist = HaversineUtil.distanceInMetres(
                                    adminLoc.getLat(), adminLoc.getLng(),
                                    memberLoc.getLat(), memberLoc.getLng());
                            if (dist > threshold) {
                                String alertMsg = String.format("📍 %s is %.0fm behind the group",
                                        memberLoc.getUserName(), dist);
                                messagingTemplate.convertAndSend("/topic/alerts/" + groupId, alertMsg);
                            }
                        }
                    }));
        });
    }

    private LocationResponse toResponse(Location l) {
        return new LocationResponse(l.getUserId(), l.getUserName(), l.getGroupId(),
                l.getLat(), l.getLng(), l.getStatus(), l.getTimestamp(), l.getAccuracy());
    }
}
