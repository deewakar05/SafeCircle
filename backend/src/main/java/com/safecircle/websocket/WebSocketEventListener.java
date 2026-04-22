package com.safecircle.websocket;

import com.safecircle.service.LocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

/**
 * Listens for WebSocket lifecycle events.
 *
 * <p>The key upgrade over Phase 3: when a user's browser closes or loses
 * connectivity the {@link SessionDisconnectEvent} fires immediately and we
 * mark that user OFFLINE + broadcast to all their groups in real-time —
 * without waiting for the 30-second polling scheduler.
 */
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final LocationService locationService;

    @Autowired
    public WebSocketEventListener(LocationService locationService) {
        this.locationService = locationService;
    }

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> attrs = accessor.getSessionAttributes();
        String userId = attrs != null ? (String) attrs.get("userId") : null;
        log.info("[WS] ▶ Session CONNECTED: sessionId={}, userId={}",
                accessor.getSessionId(), userId != null ? userId : "anonymous");
    }

    /**
     * Fired the moment the STOMP session closes (tab closed, network drop, etc.).
     * Immediately marks the user OFFLINE in all their groups and broadcasts -
     * other clients see it within milliseconds.
     */
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> attrs = accessor.getSessionAttributes();

        if (attrs == null) return;

        String userId = (String) attrs.get("userId");
        if (userId == null) {
            log.debug("[WS] ■ Anonymous session {} disconnected", event.getSessionId());
            return;
        }

        log.info("[WS] ■ User {} DISCONNECTED — marking OFFLINE instantly", userId);
        locationService.markUserOffline(userId);
    }
}
