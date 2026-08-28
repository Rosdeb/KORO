package com.koro.app.collection.repository;

import com.koro.app.collection.entity.CollectionItem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CollectionItemRepository extends MongoRepository<CollectionItem, String> {
    List<CollectionItem> findByCollectionId(String collectionId);
    Optional<CollectionItem> findByCollectionIdAndConceptIdAndLanguageId(String collectionId, String conceptId, String languageId);
    Optional<CollectionItem> findByIdAndCollectionUserId(String id, String userId);
    long countByCollectionId(String collectionId);
    void deleteByCollectionId(String collectionId);
}
