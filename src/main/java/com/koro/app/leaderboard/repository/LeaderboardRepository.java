package com.koro.app.leaderboard.repository;

import com.koro.app.activity.entity.ActivityType;
import com.koro.app.submission.entity.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class LeaderboardRepository {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserCountAggregate {
        private Object id;
        private long count;

        public String getUserIdString() {
            if (id == null) return null;
            if (id instanceof Document doc && doc.containsKey("$id")) {
                return doc.get("$id").toString();
            }
            if (id instanceof com.mongodb.DBRef dbRef) {
                return dbRef.getId().toString();
            }
            return id.toString();
        }
    }

    /**
     * Aggregates translation submissions grouped by user.
     * Supports optional status filtering and date boundaries.
     */
    public List<UserCountAggregate> getSubmissionsAggregated(
            SubmissionStatus status, LocalDateTime start, LocalDateTime end, int limit) {

        List<AggregationOperation> operations = new ArrayList<>();
        List<Criteria> criteriaList = new ArrayList<>();

        if (status != null) {
            criteriaList.add(Criteria.where("status").is(status.name()));
        }
        if (start != null) {
            criteriaList.add(Criteria.where("createdAt").gte(start));
        }
        if (end != null) {
            criteriaList.add(Criteria.where("createdAt").lte(end));
        }

        if (!criteriaList.isEmpty()) {
            operations.add(Aggregation.match(new Criteria().andOperator(criteriaList.toArray(new Criteria[0]))));
        }

        operations.add(Aggregation.group("submittedBy").count().as("count"));
        operations.add(Aggregation.sort(Sort.Direction.DESC, "count"));
        operations.add(Aggregation.limit(limit));

        Aggregation aggregation = Aggregation.newAggregation(operations);
        AggregationResults<UserCountAggregate> results = mongoTemplate.aggregate(
                aggregation, "translation_submissions", UserCountAggregate.class);
        return results.getMappedResults();
    }

    /**
     * Aggregates activity logs grouped by user.
     * Supports optional activity type filtering and date boundaries.
     */
    public List<UserCountAggregate> getActivitiesAggregated(
            ActivityType type, LocalDateTime start, LocalDateTime end, int limit) {

        List<AggregationOperation> operations = new ArrayList<>();
        List<Criteria> criteriaList = new ArrayList<>();

        if (type != null) {
            criteriaList.add(Criteria.where("activityType").is(type.name()));
        }
        if (start != null) {
            criteriaList.add(Criteria.where("createdAt").gte(start));
        }
        if (end != null) {
            criteriaList.add(Criteria.where("createdAt").lte(end));
        }

        if (!criteriaList.isEmpty()) {
            operations.add(Aggregation.match(new Criteria().andOperator(criteriaList.toArray(new Criteria[0]))));
        }

        operations.add(Aggregation.group("user").count().as("count"));
        operations.add(Aggregation.sort(Sort.Direction.DESC, "count"));
        operations.add(Aggregation.limit(limit));

        Aggregation aggregation = Aggregation.newAggregation(operations);
        AggregationResults<UserCountAggregate> results = mongoTemplate.aggregate(
                aggregation, "activity_logs", UserCountAggregate.class);
        return results.getMappedResults();
    }
}