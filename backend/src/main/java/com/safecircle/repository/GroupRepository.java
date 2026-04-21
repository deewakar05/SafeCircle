package com.safecircle.repository;

import com.safecircle.model.Group;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface GroupRepository extends MongoRepository<Group, String> {
    Optional<Group> findByInviteCode(String inviteCode);
    boolean existsByInviteCode(String inviteCode);
    List<Group> findByMemberIdsContaining(String userId);
}
