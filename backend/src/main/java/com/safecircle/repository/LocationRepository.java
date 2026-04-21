package com.safecircle.repository;

import com.safecircle.model.Location;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface LocationRepository extends MongoRepository<Location, String> {
    List<Location> findByGroupId(String groupId);
    Optional<Location> findByUserIdAndGroupId(String userId, String groupId);
    // For offline detection: find stale locations
    List<Location> findByGroupIdAndTimestampLessThan(String groupId, long threshold);
}
