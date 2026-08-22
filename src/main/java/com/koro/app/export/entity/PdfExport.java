package com.koro.app.export.entity;

import com.koro.app.collection.entity.Collection;
import com.koro.app.user.entity.User;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import java.time.LocalDateTime;

@Document(collection = "pdf_exports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfExport {

    @Id
    private String id;

    @DocumentReference(lazy = true)
    private User user;

    @DocumentReference(lazy = true)
    private Collection collection;

    private String fileUrl;

    private String fileName;

    private Long fileSize;

    @CreatedDate
    private LocalDateTime createdAt;
}
