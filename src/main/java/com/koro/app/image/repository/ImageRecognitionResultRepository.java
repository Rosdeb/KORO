package com.koro.app.image.repository;

import com.koro.app.image.entity.ImageRecognitionResult;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ImageRecognitionResultRepository extends MongoRepository<ImageRecognitionResult, String> {
    List<ImageRecognitionResult> findByUserIdOrderByCreatedAtDesc(String userId);
}
