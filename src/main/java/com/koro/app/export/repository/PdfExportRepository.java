package com.koro.app.export.repository;

import com.koro.app.export.entity.PdfExport;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PdfExportRepository extends MongoRepository<PdfExport, String> {
    List<PdfExport> findByUserIdOrderByCreatedAtDesc(String userId);
}
