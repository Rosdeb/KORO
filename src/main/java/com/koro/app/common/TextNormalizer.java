package com.koro.app.common;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Normalizes user supplied text so that searches and stored values compare consistently.
 *
 * <p>Bangla (and other Indic scripts) can represent the same visible word with different
 * Unicode code point sequences. For example the vowel sign for "ou" may arrive as a single
 * code point (U+09CC) or as the sequence U+09C7 U+09D7, and letters such as ya/dda/ddha have
 * both a precomposed form and a "base letter + nukta" form. Text typed on a phone keyboard
 * frequently also carries invisible zero-width joiners. When the query and the stored value
 * use different forms, a plain substring / regex match silently returns nothing even though
 * the words look identical, which is why English search worked but Bangla search did not.
 *
 * <p>Running both the stored value and the query through {@link Normalizer.Form#NFC} and
 * stripping zero-width characters makes the two comparable.
 */
public final class TextNormalizer {

    /**
     * Zero-width space (U+200B), zero-width non-joiner (U+200C), zero-width joiner (U+200D)
     * and BOM / zero-width no-break space (U+FEFF).
     */
    private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B\\u200C\\u200D\\uFEFF]");

    private TextNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFC);
        normalized = ZERO_WIDTH.matcher(normalized).replaceAll("");
        return normalized.trim();
    }
}
