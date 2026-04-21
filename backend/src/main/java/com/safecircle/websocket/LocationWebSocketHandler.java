package com.safecircle.websocket;

import com.safecircle.dto.LocationDto.LocationUpdateRequest;
import com.safecircle.repository.UserRepository;
import com.safecircle.service.LocationService;
import com.safecircle.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class LocationWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(LocationWebSocketHandler.class);

    private final LocationService locationService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Autowired
    public LocationWebSocketHandler(LocationService locationService,
                                    UserRepository userRepository,
                                    JwtUtil jwtUtil) {
        this.locationService = locationService;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    @MessageMapping("/location.update")
    public void handleLocationUpdate(@Payload LocationUpdateRequest request,
                                     SimpMessageHeaderAccessor headers) {
        String userId = (String) headers.getSessionAttributes().get("userId");
        if (userId == null) {
            log.warn("WebSocket location update received without userId in session");
            return;
        }
        log.debug("WS location update from user {} for group {}", userId, request.groupId());
        locationService.updateLocation(userId, request);
    }
}
