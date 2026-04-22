package com.safecircle.websocket;

import com.safecircle.dto.LocationDto.LocationUpdateRequest;
import com.safecircle.service.LocationService;
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

    @Autowired
    public LocationWebSocketHandler(LocationService locationService) {
        this.locationService = locationService;
    }

    @MessageMapping("/location.update")
    public void handleLocationUpdate(@Payload LocationUpdateRequest request,
            SimpMessageHeaderAccessor headers) {
        var attributes = headers.getSessionAttributes();
        if (attributes == null || !attributes.containsKey("userId")) {
            log.warn("WebSocket location update received without userId in session");
            return;
        }
        String userId = (String) attributes.get("userId");
        log.debug("WS location update from user {} for group {}", userId, request.groupId());
        locationService.updateLocation(userId, request);
    }
}
