package com.koro.app.export.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Getter
@Setter
public class PdfExportRequest {

    @NotBlank
    private String collectionId;

    /** Single target language — kept for backwards compatibility. Prefer {@link #languageIds}. */
    private String languageId;

    /** One or more languages to print per entry, in column order (e.g. Chakma, Bangla, English). */
    private List<String> languageIds;

    /** Language whose word is used as the entry headword. Defaults to the first resolved language. */
    private String headwordLanguageId;

    /** Include each translation's example sentence under the entry. Default true. */
    private Boolean includeExampleSentences;

    /** Merges {@code languageId} and {@code languageIds} into one de-duplicated, ordered list. */
    public List<String> resolvedLanguageIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (languageIds != null) {
            for (String id : languageIds) {
                if (id != null && !id.isBlank()) {
                    ids.add(id);
                }
            }
        }
        if (languageId != null && !languageId.isBlank()) {
            ids.add(languageId);
        }
        return new ArrayList<>(ids);
    }

    public boolean includeExampleSentencesOrDefault() {
        return includeExampleSentences == null || includeExampleSentences;
    }
}
