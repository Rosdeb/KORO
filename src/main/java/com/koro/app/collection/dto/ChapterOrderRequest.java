package com.koro.app.collection.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

/**
 * Bulk chapter rename + reorder for a book, so renaming a chapter is one call
 * instead of one PATCH per item.
 *
 * <p>Each entry names an existing chapter in {@code from}; if {@code to} is set and
 * different, every item in {@code from} is moved to {@code to}. The order of the
 * list becomes the book's chapter order (stored on the collection and honoured by
 * the PDF export). Chapters that exist on items but are not listed here keep their
 * name and are printed after the listed ones, ordered by name.
 */
@Getter
@Setter
public class ChapterOrderRequest {

    @NotEmpty
    private List<ChapterOp> chapters;

    @Getter
    @Setter
    public static class ChapterOp {
        private String from;
        private String to;
    }
}
