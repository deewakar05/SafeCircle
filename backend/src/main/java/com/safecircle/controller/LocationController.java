package com.safecircle.controller;

import com.safecircle.dto.LocationDto.*;
import com.safecircle.repository.UserRepository;
import com.safecircle.service.LocationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService locationService;
    private final UserRepository userRepository;

    @Autowired
    public LocationController(LocationService locationService, UserRepository userRepository) {
        this.locationService = locationService;
        this.userRepository = userRepository;
    }

    @PostMapping("/update")
    public ResponseEntity<LocationResponse> updateLocation(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody LocationUpdateRequest request) {
        return ResponseEntity.ok(locationService.updateLocation(getUserId(userDetails), request));
    }

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
