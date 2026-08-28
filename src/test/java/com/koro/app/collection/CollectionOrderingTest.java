package com.koro.app.collection;

import com.koro.app.collection.entity.CollectionItem;
import com.koro.app.concept.entity.Concept;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CollectionOrderingTest {

    @Test
    void resolveChapterOrderPutsPreferredFirstThenRemainingByName() {
        List<String> resolved = CollectionOrdering.resolveChapterOrder(
                List.of("Intro", "Food"),
                List.of("Travel", "Food", "Intro", "Animals"));

        assertEquals(List.of("Intro", "Food", "Animals", "Travel"), resolved);
    }

    @Test
    void resolveChapterOrderIgnoresPreferredChaptersThatDoNotExist() {
        List<String> resolved = CollectionOrdering.resolveChapterOrder(
                List.of("Ghost", "Food"),
                List.of("Food", "Animals"));

        assertEquals(List.of("Food", "Animals"), resolved);
    }

    @Test
    void resolveChapterOrderHandlesNullPreferred() {
        List<String> resolved = CollectionOrdering.resolveChapterOrder(null, List.of("B", "A"));
        assertEquals(List.of("A", "B"), resolved);
    }

    @Test
    void itemComparatorSortsByChapterThenDisplayOrderThenName() {
        CollectionItem a = item("Food", 1, "Rice");
        CollectionItem b = item("Food", 0, "Bread");
        CollectionItem c = item("Intro", 5, "Hello");
        CollectionItem d = item("Food", 0, "Apple");

        List<CollectionItem> list = new ArrayList<>(List.of(a, b, c, d));
        list.sort(CollectionOrdering.itemComparator(List.of("Intro", "Food")));

        assertEquals(List.of("Hello", "Apple", "Bread", "Rice"),
                list.stream().map(it -> it.getConcept().getName()).toList());
    }

    private static CollectionItem item(String chapter, int order, String conceptName) {
        Concept concept = new Concept();
        concept.setName(conceptName);
        CollectionItem item = new CollectionItem();
        item.setChapter(chapter);
        item.setDisplayOrder(order);
        item.setConcept(concept);
        return item;
    }
}
