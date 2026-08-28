package com.koro.app.common;

import org.junit.jupiter.api.Test;

import java.text.Normalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TextNormalizerTest {

    // Bangla "na" + i-sign etc. are spelled out with explicit code points so the test
    // does not depend on the encoding form of this source file.
    private static final String NA = "ন";
    private static final String VOWEL_SIGN_E = "ে";
    private static final String AU_LENGTH_MARK = "ৗ";
    private static final String VOWEL_SIGN_AU = "ৌ";
    private static final String ZERO_WIDTH_NON_JOINER = "‌";

    @Test
    void returnsNullForNullInput() {
        assertNull(TextNormalizer.normalize(null));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals("water", TextNormalizer.normalize("  water  "));
    }

    @Test
    void stripsZeroWidthNonJoiner() {
        assertEquals("ab", TextNormalizer.normalize("a" + ZERO_WIDTH_NON_JOINER + "b"));
    }

    @Test
    void collapsesCanonicallyEquivalentBanglaSequences() {
        String precomposed = NA + VOWEL_SIGN_AU;
        String decomposed = NA + VOWEL_SIGN_E + AU_LENGTH_MARK;

        // The decomposed spelling is not byte-equal to the precomposed one ...
        assertEquals(2, precomposed.length());
        assertEquals(3, decomposed.length());
        // ... but after normalization the two are identical, so a search for one finds the other.
        assertEquals(precomposed, TextNormalizer.normalize(decomposed));
        assertEquals(Normalizer.normalize(precomposed, Normalizer.Form.NFC),
                TextNormalizer.normalize(decomposed));
    }
}
