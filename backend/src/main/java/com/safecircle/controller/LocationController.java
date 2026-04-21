package com.safecircle.controller;

import com.safecircle.dto.LocationDto.*;
import com.safecircle.repository.UserRepository;
import com.safecircle.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;
    private final UserRepository userRepository;

    /** REST: push a location update */
    @PostMapping("/update")
    public ResponseEntity<LocationResponse> updateLocation(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody LocationUpdateRequest request) {
        String userId = getUserId(userDetails);
        return ResponseEntity.ok(locationService.updateLocation(userId, request));
    }

    /** REST: fetch all member locations for a group */
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<LocationResponse>> getGroupLocations(@PathVariable String groupId) {
        return ResponseEntity.ok(locationService.getGroupLocations(groupId));
    }

    private String getUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"))
                .getId();
    }
}
