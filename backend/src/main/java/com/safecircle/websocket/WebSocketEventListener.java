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
 * <h3>Multi-session disconnect (Bug Fix)</h3>
 * <p>Previously, closing <em>any</em> tab immediately marked the user OFFLINE
 * across all groups, even if they had other active sessions open (e.g. another
 * browser tab or mobile device).</p>
 *
 * <p>Fixed: we consult {@link UserSessionRegistry} and only call
 * {@code markUserOffline} when the disconnected session was the user's
 * <em>last</em> one.</p>
 */
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final LocationService     locationService;
    private final UserSessionRegistry sessionRegistry;

    @Autowired
    public WebSocketEventListener(LocationService locationService,
                                  UserSessionRegistry sessionRegistry) {
        this.locationService  = locationService;
        this.sessionRegistry  = sessionRegistry;
    }

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> attrs = accessor.getSessionAttributes();
        String userId = (attrs != null) ? (String) attrs.get("userId") : null;
        log.info("[WS] ▶ CONNECTED: sessionId={} userId={}",
                accessor.getSessionId(), userId != null ? userId : "anonymous");
    }

    /**
     * Fired the moment a STOMP session closes (tab closed, network drop, etc.).
     *
     * <p>Deregisters the session from the registry. If this was the user's last
     * session, marks them OFFLINE in all groups and broadcasts the change — other
     * clients see it within milliseconds.</p>
     *
     * <p>If the user still has other active sessions (multi-tab), we do nothing
     * and let them stay ONLINE.</p>
     */
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();

        // Deregister returns the userId that owned this session (null if anonymous)
        String userId = sessionRegistry.deregister(sessionId);

        if (userId == null) {
            log.debug("[WS] ■ Anonymous session {} disconnected", sessionId);
            return;
        }

        int remainingSessions = sessionRegistry.getSessionCount(userId);

        if (remainingSessions > 0) {
            // User still has other active tabs/devices — stay ONLINE
            log.info("[WS] ■ Session {} closed for user {} but {} session(s) still active — staying ONLINE",
                    sessionId, userId, remainingSessions);
            return;
        }

        // Last session closed — mark OFFLINE
        log.info("[WS] ■ User {} DISCONNECTED (last session closed) — marking OFFLINE", userId);
        locationService.markUserOffline(userId);
    }
}
