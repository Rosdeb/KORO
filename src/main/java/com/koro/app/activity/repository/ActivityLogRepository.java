package com.koro.app.activity.repository;

import com.koro.app.activity.entity.ActivityLog;
import com.koro.app.activity.entity.ActivityType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface ActivityLogRepository extends MongoRepository<ActivityLog, String> {
    Page<ActivityLog> findByUserId(String userId, Pageable pageable);
    Page<ActivityLog> findByUserIdAndCreatedAtBetween(String userId, LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    long countByUserId(String userId);
    long countByUserIdAndActivityType(String userId, ActivityType activityType);
    long countByUserIdAndCreatedAtBetween(String userId, LocalDateTime start, LocalDateTime end);
    long countByCreatedAtAfter(LocalDateTime start);
    
    long countByUserIdAndActivityTypeAndCreatedAtBetween(String userId, ActivityType activityType, LocalDateTime start, LocalDateTime end);
}
