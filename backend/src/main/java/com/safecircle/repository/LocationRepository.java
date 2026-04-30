package com.safecircle.repository;

import com.safecircle.model.Location;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends MongoRepository<Location, String> {

    /** All locations for a group (used for initial map load and broadcasts). */
    List<Location> findByGroupId(String groupId);

    /** Single user+group record (used for upsert logic). */
    Optional<Location> findByUserIdAndGroupId(String userId, String groupId);

    /** All locations for a user across all groups (used on disconnect). */
    List<Location> findByUserId(String userId);

    /**
     * Locations in a group where the timestamp is older than the given threshold
     * (used by the offline-detection scheduler per group).
     */
    List<Location> findByGroupIdAndTimestampLessThan(String groupId, long threshold);

    /**
     * All non-OFFLINE locations that have gone stale — used by the optimised
     * scheduler to avoid a full groupRepository.findAll() scan.
     *
     * <p>The index on {@code timestamp} (defined in {@link com.safecircle.model.Location})
     * makes this query efficient even at scale.
     */
    List<Location> findByStatusNotAndTimestampLessThan(String status, long threshold);
}
