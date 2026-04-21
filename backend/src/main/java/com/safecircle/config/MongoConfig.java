package com.safecircle.config;

import com.safecircle.model.Group;
import com.safecircle.model.Location;
import com.safecircle.model.User;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

@Configuration
@EnableMongoAuditing
public class MongoConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoConfig.class);

    private final MongoTemplate mongoTemplate;
    private final MongoMappingContext mongoMappingContext;

    @Autowired
    public MongoConfig(MongoTemplate mongoTemplate, MongoMappingContext mongoMappingContext) {
        this.mongoTemplate = mongoTemplate;
        this.mongoMappingContext = mongoMappingContext;
    }

    /**
     * Ensures @Indexed annotations (unique email, inviteCode, etc.) are applied
     * at startup. Logs a warning if MongoDB is unreachable instead of crashing.
     */
    @PostConstruct
    public void initIndexes() {
        try {
            IndexResolver resolver = new MongoPersistentEntityIndexResolver(mongoMappingContext);
            for (Class<?> entity : new Class[]{User.class, Group.class, Location.class}) {
                IndexOperations indexOps = mongoTemplate.indexOps(entity);
                resolver.resolveIndexFor(entity).forEach(indexOps::ensureIndex);
            }
            log.info("MongoDB indexes initialized successfully");
        } catch (Exception e) {
            log.warn("Could not initialize MongoDB indexes (is MongoDB running?): {}", e.getMessage());
        }
    }
}
