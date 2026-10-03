package com.koro.app.concept.service;

import com.koro.app.concept.dto.ConceptDetails;
import com.koro.app.concept.entity.Concept;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class ConceptReadService {
    private static final Logger logger = LoggerFactory.getLogger(ConceptReadService.class);
    private final MongoTemplate mongoTemplate;

    public ConceptReadService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<ConceptDetails> findById(String id) {
        // Match first to use the concept's _id index before joining the category.
        // Typed aggregation preserves Spring's String/ObjectId ID conversion.
        var aggregation = Aggregation.newAggregation(Concept.class,
                Aggregation.match(Criteria.where("id").is(id)),
                Aggregation.lookup("categories", "category", "_id", "category"),
                Aggregation.unwind("category", true));

        long started = System.nanoTime();
        try {
            return Optional.ofNullable(mongoTemplate.aggregate(aggregation, ConceptDetails.class)
                    .getUniqueMappedResult());
        } finally {
            logger.debug("Concept detail database request completed in {} ms",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
    }
}
