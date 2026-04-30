package com.safecircle.websocket;

import com.safecircle.repository.GroupRepository;
import com.safecircle.repository.UserRepository;
import com.safecircle.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Intercepts STOMP frames on the inbound channel to enforce:
 *
 * <ul>
 *   <li><b>CONNECT</b> — validates JWT, stores userId in session attributes,
 *       registers the session in {@link UserSessionRegistry}.</li>
 *   <li><b>SUBSCRIBE</b> — authorises that the authenticated user is a member
 *       of the group they are subscribing to. Rejects with an exception if not.</li>
 *   <li><b>SEND (/app/…)</b> — passes through; the handler itself re-validates
 *       membership via {@code LocationService}.</li>
 * </ul>
 */
@Component
public class WebSocketChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketChannelInterceptor.class);

    // Destination prefixes we want to authorise on SUBSCRIBE
    private static final String GROUP_TOPIC_PREFIX  = "/topic/group/";
    private static final String ALERTS_TOPIC_PREFIX = "/topic/alerts/";

    private final JwtUtil              jwtUtil;
    private final UserRepository       userRepository;
    private final GroupRepository      groupRepository;
    private final UserSessionRegistry  sessionRegistry;

    @Autowired
    public WebSocketChannelInterceptor(JwtUtil jwtUtil,
                                       UserRepository userRepository,
                                       GroupRepository groupRepository,
                                       UserSessionRegistry sessionRegistry) {
        this.jwtUtil         = jwtUtil;
        this.userRepository  = userRepository;
        this.groupRepository = groupRepository;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        StompCommand command = accessor.getCommand();
        if (command == null)  return message;

        switch (command) {
            case CONNECT   -> handleConnect(accessor);
            case SUBSCRIBE -> handleSubscribe(accessor);
            default        -> { /* pass through */ }
        }

        return message;
    }

    // ─────────────────────────────────────────────────────────
    // CONNECT: authenticate and register session
    // ─────────────────────────────────────────────────────────

    private void handleConnect(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("[WS] CONNECT without Authorization header — rejecting");
            throw new SecurityException("Missing or invalid Authorization header on STOMP CONNECT");
        }

        String jwt = authHeader.substring(7);
        if (!jwtUtil.isTokenValid(jwt)) {
            log.warn("[WS] CONNECT with invalid/expired JWT — rejecting");
            throw new SecurityException("Invalid or expired JWT on STOMP CONNECT");
        }

        String email = jwtUtil.extractEmail(jwt);
        userRepository.findByEmail(email).ifPresentOrElse(
            user -> {
                Map<String, Object> attrs = accessor.getSessionAttributes();
                if (attrs != null) {
                    attrs.put("userId", user.getId());
                    attrs.put("email",  email);
                }
                // Register this session in the presence registry
                sessionRegistry.register(user.getId(), accessor.getSessionId());
                log.info("[WS] ▶ CONNECT authenticated: user={} ({}) session={}",
                        user.getName(), user.getId(), accessor.getSessionId());
            },
            () -> {
                log.warn("[WS] JWT valid but user not found in DB: {} — rejecting", email);
                throw new SecurityException("Authenticated user not found");
            }
        );
    }

    // ─────────────────────────────────────────────────────────
    // SUBSCRIBE: authorise group membership
    // ─────────────────────────────────────────────────────────

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) return;

        // Only enforce auth on group-scoped topics
        String groupId = extractGroupId(destination);
        if (groupId == null) return;

        String userId = getUserId(accessor);
        if (userId == null) {
            log.warn("[WS] SUBSCRIBE to {} attempted by unauthenticated session — rejecting", destination);
            throw new SecurityException("Authentication required to subscribe to group topics");
        }

        boolean isMember = groupRepository.findById(groupId)
                .map(g -> g.getMemberIds().contains(userId))
                .orElse(false);

        if (!isMember) {
            log.warn("[WS] SUBSCRIBE DENIED: user {} is not a member of group {}", userId, groupId);
            throw new SecurityException("Access denied: you are not a member of group " + groupId);
        }

        log.debug("[WS] SUBSCRIBE authorised: user={} group={} destination={}", userId, groupId, destination);
    }

    // ─────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────

    /**
     * Extracts groupId from destinations like:
     *   /topic/group/{groupId}
     *   /topic/alerts/{groupId}
     * Returns null for any other destination (no auth required).
     */
    private String extractGroupId(String destination) {
        if (destination.startsWith(GROUP_TOPIC_PREFIX)) {
            return destination.substring(GROUP_TOPIC_PREFIX.length());
        }
        if (destination.startsWith(ALERTS_TOPIC_PREFIX)) {
            return destination.substring(ALERTS_TOPIC_PREFIX.length());
        }
        return null;
    }

    private String getUserId(StompHeaderAccessor accessor) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        return (attrs != null) ? (String) attrs.get("userId") : null;
    }
}
