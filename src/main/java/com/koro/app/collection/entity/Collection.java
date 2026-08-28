package com.koro.app.collection.entity;

import com.koro.app.user.entity.User;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "collections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Collection {

    @Id
    private String id;

    @DocumentReference(lazy = true)
    private User user;

    private String name;

    private String description;

    /**
     * Chapter names in the order the author wants them printed. May be null / partial;
     * any chapter not listed here is appended after these, ordered by name. Kept as a
     * plain ordered list rather than a chapter entity because chapters have no data of
     * their own beyond a name and a position.
     */
    private List<String> chapterOrder;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
