package com.safecircle.websocket;

import com.safecircle.dto.LocationDto.LocationUpdateRequest;
import com.safecircle.repository.UserRepository;
import com.safecircle.service.LocationService;
import com.safecircle.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class LocationWebSocketHandler {

    private final LocationService locationService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    /**
     * Handles STOMP messages sent by clients to /app/location.update
     * Client must include JWT token as first message field for auth.
     */
    @MessageMapping("/location.update")
    public void handleLocationUpdate(@Payload LocationUpdateRequest request,
                                     org.springframework.messaging.simp.SimpMessageHeaderAccessor headers) {
        // Extract user from STOMP session attributes (set during handshake)
        String userId = (String) headers.getSessionAttributes().get("userId");
        if (userId == null) {
            log.warn("WebSocket location update received without userId in session");
            return;
        }
        log.debug("WS location update from user {} for group {}", userId, request.groupId());
        locationService.updateLocation(userId, request);
    }
}
