package com.koro.app.collection;

import com.koro.app.collection.entity.CollectionItem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared ordering rules for a book's chapters and words, used by both the
 * collection detail endpoint and the PDF export so every surface renders a book
 * in the same order.
 */
public final class CollectionOrdering {

    private CollectionOrdering() {
    }

    /**
     * Full chapter order for a book: the author's {@code preferred} order first
     * (limited to chapters that actually exist), then any remaining chapters
     * sorted by name.
     */
    public static List<String> resolveChapterOrder(List<String> preferred, Collection<String> present) {
        List<String> ordered = new ArrayList<>();
        if (preferred != null) {
            for (String chapter : preferred) {
                if (present.contains(chapter) && !ordered.contains(chapter)) {
                    ordered.add(chapter);
                }
            }
        }
        List<String> remaining = new ArrayList<>();
        for (String chapter : present) {
            if (!ordered.contains(chapter)) {
                remaining.add(chapter);
            }
        }
        Collections.sort(remaining);
        ordered.addAll(remaining);
        return ordered;
    }

    /** Sorts items by chapter (per {@code chapterOrder}), then {@code displayOrder}, then concept name. */
    public static Comparator<CollectionItem> itemComparator(List<String> chapterOrder) {
        Map<String, Integer> rank = new HashMap<>();
        if (chapterOrder != null) {
            for (int i = 0; i < chapterOrder.size(); i++) {
                rank.put(chapterOrder.get(i), i);
            }
        }
        return Comparator
                .comparingInt((CollectionItem it) -> rank.getOrDefault(chapterOf(it), Integer.MAX_VALUE))
                .thenComparing(CollectionOrdering::chapterOf)
                .thenComparingInt(it -> it.getDisplayOrder() == null ? 0 : it.getDisplayOrder())
                .thenComparing(CollectionOrdering::conceptNameOf, String.CASE_INSENSITIVE_ORDER);
    }

    private static String chapterOf(CollectionItem it) {
        return it.getChapter() == null ? "" : it.getChapter();
    }

    private static String conceptNameOf(CollectionItem it) {
        return it.getConcept() != null && it.getConcept().getName() != null ? it.getConcept().getName() : "";
    }
}
