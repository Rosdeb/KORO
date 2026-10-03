package com.koro.app.concept.service;

import com.koro.app.concept.dto.ConceptDetails;
import com.koro.app.concept.entity.Concept;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.TypedAggregation;
import org.springframework.data.mongodb.core.aggregation.TypeBasedAggregationOperationContext;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.convert.QueryMapper;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConceptReadServiceTest {
    private MongoTemplate mongoTemplate;
    private ConceptReadService service;
    private MappingMongoConverter converter;
    private TypeBasedAggregationOperationContext context;

    @BeforeEach
    void setUp() throws Exception {
        mongoTemplate = mock(MongoTemplate.class);
        service = new ConceptReadService(mongoTemplate);
        var mappingContext = new MongoMappingContext();
        mappingContext.afterPropertiesSet();
        converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mappingContext);
        converter.afterPropertiesSet();
        context = new TypeBasedAggregationOperationContext(Concept.class, mappingContext, new QueryMapper(converter));
    }

    @Test
    void joinsCategoryInOneRequestWithIndexedObjectIdMatch() {
        String id = "6aac2d642bf8fd7d979bd3a2";
        var categoryId = new ObjectId("6a8df2cedcd3a0ea1917180c");
        var document = new Document("_id", new ObjectId(id)).append("name", "where")
                .append("category", new Document("_id", categoryId).append("name", "Daily Life")
                        .append("description", "Everyday words"));
        var details = converter.read(ConceptDetails.class, document);
        when(mongoTemplate.aggregate(any(TypedAggregation.class), eq(ConceptDetails.class)))
                .thenReturn(new AggregationResults<>(List.of(details), new Document()));

        var result = service.findById(id).orElseThrow();

        assertEquals(id, result.id());
        assertEquals(categoryId.toHexString(), result.category().id());
        assertEquals("Daily Life", result.category().name());
        assertNull(result.description());
        assertNull(result.referenceImage());
        var json = JsonMapper.builder().build().valueToTree(result);
        assertEquals(5, json.size());
        assertEquals(id, json.get("id").asString());
        assertEquals("where", json.get("name").asString());
        assertTrue(json.get("description").isNull());
        assertTrue(json.get("referenceImage").isNull());
        assertEquals(3, json.get("category").size());
        assertEquals(categoryId.toHexString(), json.get("category").get("id").asString());
        assertEquals("Everyday words", json.get("category").get("description").asString());
        var pipeline = capturedPipeline();
        assertEquals(new Document("$match", new Document("_id", new ObjectId(id))), pipeline.get(0));
        assertEquals(new Document("$lookup", new Document("from", "categories")
                .append("localField", "category").append("foreignField", "_id").append("as", "category")), pipeline.get(1));
        assertEquals(new Document("$unwind", new Document("path", "$category")
                .append("preserveNullAndEmptyArrays", true)), pipeline.get(2));
        verifyNoMoreInteractions(mongoTemplate);
    }

    @Test
    void keepsConceptWithoutCategoryAndSupportsStringIds() {
        var details = converter.read(ConceptDetails.class, new Document("_id", "custom-id").append("name", "where"));
        when(mongoTemplate.aggregate(any(TypedAggregation.class), eq(ConceptDetails.class)))
                .thenReturn(new AggregationResults<>(List.of(details), new Document()));

        assertNull(service.findById("custom-id").orElseThrow().category());
        assertEquals(new Document("$match", new Document("_id", "custom-id")), capturedPipeline().get(0));
    }

    @Test
    void returnsEmptyForMissingConcept() {
        when(mongoTemplate.aggregate(any(TypedAggregation.class), eq(ConceptDetails.class)))
                .thenReturn(new AggregationResults<>(List.of(), new Document()));
        assertTrue(service.findById("missing").isEmpty());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<Document> capturedPipeline() {
        ArgumentCaptor<TypedAggregation> captor = ArgumentCaptor.forClass(TypedAggregation.class);
        verify(mongoTemplate).aggregate(captor.capture(), eq(ConceptDetails.class));
        return captor.getValue().toPipeline(context);
    }
}
