package com.safecircle.service;

import com.safecircle.dto.LocationDto.*;
import com.safecircle.model.Group;
import com.safecircle.model.Location;
import com.safecircle.repository.GroupRepository;
import com.safecircle.repository.LocationRepository;
import com.safecircle.repository.UserRepository;
import com.safecircle.util.HaversineUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    /** A location record older than this is considered stale / OFFLINE. */
    private static final long OFFLINE_THRESHOLD_MS = 30_000;

    /**
     * Minimum milliseconds between DB writes for the same user+group pair.
     * Broadcasts still fire on every call; only the DB write is throttled.
     */
    private static final long RATE_LIMIT_MS = 2_000;

    private final LocationRepository    locationRepository;
    private final GroupRepository       groupRepository;
    private final UserRepository        userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * In-memory rate-limit store: "userId:groupId" → last-write timestamp.
     * ConcurrentHashMap is safe for concurrent GPS ticks.
     */
    private final Map<String, Long> lastWriteTs = new ConcurrentHashMap<>();

    /**
     * Simple in-memory cache for group configuration (adminId + distanceThreshold).
     * Populated on first access, invalidated when group is updated.
     * Eliminates the N+1 groupRepository.findById on every GPS tick.
     */
    private final Map<String, Group> groupCache = new ConcurrentHashMap<>();

    @Autowired
    public LocationService(LocationRepository locationRepository,
                           GroupRepository groupRepository,
                           UserRepository userRepository,
                           SimpMessagingTemplate messagingTemplate) {
        this.locationRepository = locationRepository;
        this.groupRepository    = groupRepository;
        this.userRepository     = userRepository;
        this.messagingTemplate  = messagingTemplate;
    }

    // ─────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────

    /**
     * Update a user's location for a given group.
     *
     * <h3>Security</h3>
     * Validates that {@code userId} is a member of {@code request.groupId()}.
     * Throws {@link SecurityException} (→ HTTP 403) if not.
     *
     * <h3>Rate limiting</h3>
     * DB writes are throttled to at most one write per {@value #RATE_LIMIT_MS}ms
     * per user+group pair. The WebSocket broadcast still fires every time so the
     * map stays smooth — only the persistence is debounced.
     */
    public LocationResponse updateLocation(String userId, LocationUpdateRequest request) {
        // ── 1. IDOR/BOLA guard ──────────────────────────────────
        Group group = getCachedGroup(request.groupId());
        if (!group.getMemberIds().contains(userId)) {
            log.warn("[SECURITY] User {} attempted location update for group {} (not a member)",
                    userId, request.groupId());
            throw new SecurityException("Access denied: you are not a member of this group");
        }

        // ── 2. Resolve user display name ────────────────────────
        String userName = userRepository.findById(userId)
                .map(u -> u.getName())
                .orElse("Unknown");

        // ── 3. Upsert location record ────────────────────────────
        Location location = locationRepository
                .findByUserIdAndGroupId(userId, request.groupId())
                .orElse(Location.builder().userId(userId).groupId(request.groupId()).build());

        location.setLat(request.lat());
        location.setLng(request.lng());
        location.setStatus(request.status() != null ? request.status() : "ONLINE");
        location.setTimestamp(System.currentTimeMillis());
        location.setUserName(userName);
        location.setAccuracy(request.accuracy());

        // ── 4. Rate-limited DB write ─────────────────────────────
        String rateKey = userId + ":" + request.groupId();
        long now = System.currentTimeMillis();
        long lastWrite = lastWriteTs.getOrDefault(rateKey, 0L);

        if (now - lastWrite >= RATE_LIMIT_MS) {
            locationRepository.save(location);
            lastWriteTs.put(rateKey, now);
            log.debug("[Location] DB write for user={} group={}", userId, request.groupId());
        } else {
            log.debug("[Location] Rate-limited DB write for user={} ({}ms < {}ms threshold)",
                    userId, now - lastWrite, RATE_LIMIT_MS);
        }

        // ── 5. Always broadcast (even if DB write was throttled) ─
        LocationResponse response = toResponse(location);
        messagingTemplate.convertAndSend("/topic/group/" + request.groupId(), response);

        // ── 6. SOS broadcast ─────────────────────────────────────
        if ("SOS".equals(request.status())) {
            String sosMsg = "🚨 " + userName + " TRIGGERED SOS!";
            messagingTemplate.convertAndSend("/topic/alerts/" + request.groupId(), sosMsg);
            log.warn("[SOS] User {} in group {} — {}", userId, request.groupId(), sosMsg);
        }

        // ── 7. Distance alerts ───────────────────────────────────
        checkDistanceAlerts(request.groupId(), location, group);

        return response;
    }

    /**
     * Returns all current locations for the given group.
     *
     * <h3>Security</h3>
     * Validates that {@code userId} is a member of {@code groupId}.
     */
    public List<LocationResponse> getGroupLocations(String groupId, String userId) {
        Group group = getCachedGroup(groupId);
        if (!group.getMemberIds().contains(userId)) {
            log.warn("[SECURITY] User {} attempted to read locations for group {} (not a member)",
                    userId, groupId);
            throw new SecurityException("Access denied: you are not a member of this group");
        }
        return locationRepository.findByGroupId(groupId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Called immediately when a WebSocket session's <em>last</em> connection closes.
     * Marks the user OFFLINE in every group they were active in and broadcasts
     * the change in real-time.
     */
    public void markUserOffline(String userId) {
        locationRepository.findByUserId(userId).forEach(loc -> {
            if (!"OFFLINE".equals(loc.getStatus())) {
                loc.setStatus("OFFLINE");
                loc.setTimestamp(System.currentTimeMillis());
                locationRepository.save(loc);

                messagingTemplate.convertAndSend("/topic/group/" + loc.getGroupId(), toResponse(loc));
                messagingTemplate.convertAndSend(
                        "/topic/alerts/" + loc.getGroupId(),
                        "📡 " + loc.getUserName() + " disconnected");

                log.info("[WS] Marked user {} OFFLINE in group {} (all sessions closed)",
                        userId, loc.getGroupId());
            }
        });
    }

    // ─────────────────────────────────────────────────────────
    // Scheduled: stale-user offline detection
    // ─────────────────────────────────────────────────────────

    /**
     * Fallback scheduler: scans for ONLINE/SOS/NO_GPS records that haven't
     * received an update in {@value #OFFLINE_THRESHOLD_MS}ms.
     *
     * <h3>Optimisation (replaces the old findAll() bottleneck)</h3>
     * <p>Previously this called {@code groupRepository.findAll()} to iterate every
     * group then query locations per group — O(groups) DB calls every 15 seconds.</p>
     *
     * <p>Now it queries the {@code locations} collection directly for stale
     * non-OFFLINE records — O(1) indexed query regardless of group count.</p>
     */
    @Scheduled(fixedRate = 15_000)
    public void detectOfflineUsers() {
        long threshold = System.currentTimeMillis() - OFFLINE_THRESHOLD_MS;

        List<Location> stale = locationRepository
                .findByStatusNotAndTimestampLessThan("OFFLINE", threshold);

        if (stale.isEmpty()) return;

        log.debug("[Scheduler] Found {} stale location(s) to mark OFFLINE", stale.size());

        stale.forEach(loc -> {
            loc.setStatus("OFFLINE");
            locationRepository.save(loc);
            messagingTemplate.convertAndSend(
                    "/topic/alerts/" + loc.getGroupId(),
                    "⚠️ " + loc.getUserName() + " is OFFLINE");
            log.info("[Scheduler] Offline alert: user={} group={}", loc.getUserId(), loc.getGroupId());
        });
    }

    // ─────────────────────────────────────────────────────────
    // Cache management
    // ─────────────────────────────────────────────────────────

    /**
     * Invalidates the cached group entry. Call this whenever a group is mutated
     * (threshold change, member add/remove, route update) so the next GPS tick
     * picks up the fresh config.
     */
    public void evictGroupCache(String groupId) {
        groupCache.remove(groupId);
        log.debug("[Cache] Evicted group cache for {}", groupId);
    }

    // ─────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────

    /**
     * Returns a cached group, fetching from DB on first access.
     * Throws {@link IllegalArgumentException} if not found.
     */
    private Group getCachedGroup(String groupId) {
        return groupCache.computeIfAbsent(groupId, id ->
                groupRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Group not found: " + id)));
    }

    /**
     * Check if any member has drifted beyond the group's distance threshold
     * relative to the admin and broadcast an alert if so.
     *
     * <p>Uses the already-fetched {@code group} object to avoid an extra DB call
     * (the N+1 fix — group is passed in from the caller who already has it).</p>
     */
    private void checkDistanceAlerts(String groupId, Location updatedLocation, Group group) {
        double threshold = group.getDistanceThreshold();
        if (threshold <= 0) return; // distance alerts disabled for this group

        List<Location> allLocations = locationRepository.findByGroupId(groupId);

        Optional<Location> adminLocOpt = allLocations.stream()
                .filter(loc -> loc.getUserId().equals(group.getAdminId()))
                .findFirst();

        adminLocOpt.ifPresent(adminLoc ->
                allLocations.forEach(memberLoc -> {
                    if (memberLoc.getUserId().equals(group.getAdminId())) return;
                    if (memberLoc.getLat() == 0 && memberLoc.getLng() == 0) return; // no fix yet

                    double dist = HaversineUtil.distanceInMetres(
                            adminLoc.getLat(), adminLoc.getLng(),
                            memberLoc.getLat(), memberLoc.getLng());

                    if (dist > threshold) {
                        String alertMsg = String.format(
                                "📍 %s is %.0fm behind the group", memberLoc.getUserName(), dist);
                        messagingTemplate.convertAndSend("/topic/alerts/" + groupId, alertMsg);
                        log.debug("[Distance] Alert sent: user={} dist={}m threshold={}m",
                                memberLoc.getUserId(), Math.round(dist), Math.round(threshold));
                    }
                })
        );
    }

    private LocationResponse toResponse(Location l) {
        return new LocationResponse(
                l.getUserId(), l.getUserName(), l.getGroupId(),
                l.getLat(), l.getLng(), l.getStatus(),
                l.getTimestamp(), l.getAccuracy());
    }
}
