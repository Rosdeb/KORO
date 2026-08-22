package com.koro.app.activity.repository;

import com.koro.app.activity.entity.ActivityLog;
import com.koro.app.activity.entity.ActivityType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActivityLogRepository extends MongoRepository<ActivityLog, String> {
    List<ActivityLog> findByUserIdOrderByCreatedAtDesc(String userId);
    List<ActivityLog> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(String userId, LocalDateTime start, LocalDateTime end);
    
    long countByUserId(String userId);
    long countByUserIdAndActivityType(String userId, ActivityType activityType);
    long countByUserIdAndCreatedAtBetween(String userId, LocalDateTime start, LocalDateTime end);
    long countByCreatedAtAfter(LocalDateTime start);
    
    long countByUserIdAndActivityTypeAndCreatedAtBetween(String userId, ActivityType activityType, LocalDateTime start, LocalDateTime end);
}
