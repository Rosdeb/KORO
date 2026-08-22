package com.koro.app.collection.repository;

import com.koro.app.collection.entity.Collection;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CollectionRepository extends MongoRepository<Collection, String> {
    List<Collection> findByUserId(String userId);
    Optional<Collection> findByIdAndUserId(String id, String userId);
}
