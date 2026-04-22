package com.safecircle.websocket;

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
 * Intercepts every STOMP CONNECT frame, validates the JWT passed in the
 * Authorization header, and stores the authenticated userId in the WebSocket
 * session attributes so downstream handlers can identify who sent each message.
 *
 * Without this interceptor every WS location update is silently dropped because
 * LocationWebSocketHandler can't find a userId in the session.
 */
@Component
public class WebSocketChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketChannelInterceptor.class);

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Autowired
    public WebSocketChannelInterceptor(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // Only handle CONNECT frames — everything else passes straight through
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("[WS] CONNECT without Authorization header — session will be anonymous");
            return message; // allow but no userId set; handler will reject updates gracefully
        }

        String jwt = authHeader.substring(7);
        if (!jwtUtil.isTokenValid(jwt)) {
            log.warn("[WS] CONNECT with invalid/expired JWT — session will be anonymous");
            return message;
        }

        String email = jwtUtil.extractEmail(jwt);
        userRepository.findByEmail(email).ifPresentOrElse(
            user -> {
                Map<String, Object> attrs = accessor.getSessionAttributes();
                if (attrs != null) {
                    attrs.put("userId", user.getId());
                    attrs.put("email",  email);
                    log.info("[WS] Authenticated: {} ({}) — session {}",
                            user.getName(), user.getId(), accessor.getSessionId());
                }
            },
            () -> log.warn("[WS] JWT valid but user not found in DB: {}", email)
        );

        return message;
    }
}
