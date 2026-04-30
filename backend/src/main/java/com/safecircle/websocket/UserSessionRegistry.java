package com.safecircle.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry that tracks the set of active WebSocket session IDs
 * for each authenticated user.
 *
 * <p>Used to implement correct multi-tab / multi-device presence logic:
 * a user is only marked OFFLINE when <em>all</em> their sessions have closed,
 * not just the first one.
 */
@Component
public class UserSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(UserSessionRegistry.class);

    // userId → set of active sessionIds
    private final Map<String, Set<String>> userSessions = new ConcurrentHashMap<>();

    // sessionId → userId  (reverse lookup for disconnect events)
    private final Map<String, String> sessionUsers = new ConcurrentHashMap<>();

    /**
     * Register a new WebSocket session for the given user.
     */
    public void register(String userId, String sessionId) {
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        sessionUsers.put(sessionId, userId);
        log.debug("[Registry] Registered session {} for user {} (total sessions: {})",
                sessionId, userId, userSessions.get(userId).size());
    }

    /**
     * Remove a session. Returns the userId that owned it, or null if unknown.
     */
    public String deregister(String sessionId) {
        String userId = sessionUsers.remove(sessionId);
        if (userId != null) {
            Set<String> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
            log.debug("[Registry] Deregistered session {} for user {} (remaining sessions: {})",
                    sessionId, userId, getSessionCount(userId));
        }
        return userId;
    }

    /**
     * Returns the number of active sessions for a user (0 if none).
     */
    public int getSessionCount(String userId) {
        Set<String> sessions = userSessions.get(userId);
        return (sessions == null) ? 0 : sessions.size();
    }

    /**
     * Returns true if the user has NO remaining active sessions.
     */
    public boolean hasNoActiveSessions(String userId) {
        return getSessionCount(userId) == 0;
    }

    /**
     * Returns an unmodifiable view of all active sessions for a user.
     */
    public Set<String> getSessions(String userId) {
        return Collections.unmodifiableSet(
                userSessions.getOrDefault(userId, Collections.emptySet()));
    }
}
