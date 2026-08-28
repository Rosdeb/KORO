package com.koro.app.export.service;

import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.pdf.BaseFont;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.InputStream;

/**
 * Supplies embedded Unicode fonts for the PDF export, picking a face per script so
 * Bangla and Chakma render instead of coming out as blank boxes (the default
 * Helvetica Type1 font has no Unicode support).
 *
 * <p><b>Font files are not bundled.</b> Drop these TTFs into
 * {@code src/main/resources/fonts/} to enable non-Latin rendering:
 * <ul>
 *   <li>{@code NotoSans-Regular.ttf}, {@code NotoSans-Bold.ttf} — Latin</li>
 *   <li>{@code NotoSansBengali-Regular.ttf}, {@code NotoSansBengali-Bold.ttf} — Bangla</li>
 *   <li>{@code NotoSansChakma-Regular.ttf} — Chakma</li>
 * </ul>
 * All are available under the SIL Open Font License from
 * <a href="https://fonts.google.com/noto">Google Noto</a>. When a file is missing the
 * provider falls back to Helvetica for that script (Latin stays fine; Bangla/Chakma do not).
 */
@Component
public class PdfFontProvider {

    private static final Logger log = LoggerFactory.getLogger(PdfFontProvider.class);

    private static final String LATIN_REGULAR = "fonts/NotoSans-Regular.ttf";
    private static final String LATIN_BOLD = "fonts/NotoSans-Bold.ttf";
    private static final String BENGALI_REGULAR = "fonts/NotoSansBengali-Regular.ttf";
    private static final String BENGALI_BOLD = "fonts/NotoSansBengali-Bold.ttf";
    private static final String CHAKMA_REGULAR = "fonts/NotoSansChakma-Regular.ttf";

    private final BaseFont latin;
    private final BaseFont latinBold;
    private final BaseFont bengali;
    private final BaseFont bengaliBold;
    private final BaseFont chakma;

    public PdfFontProvider() {
        this.latin = load(LATIN_REGULAR);
        this.latinBold = load(LATIN_BOLD);
        this.bengali = load(BENGALI_REGULAR);
        this.bengaliBold = load(BENGALI_BOLD);
        this.chakma = load(CHAKMA_REGULAR);
        if (latin == null && bengali == null && chakma == null) {
            log.warn("No PDF fonts found under classpath:/fonts/ — exports use Helvetica and cannot render "
                    + "Bangla/Chakma. Add NotoSans-Regular.ttf, NotoSans-Bold.ttf, NotoSansBengali-Regular.ttf, "
                    + "NotoSansBengali-Bold.ttf and NotoSansChakma-Regular.ttf.");
        }
    }

    /** Whether at least one embedded Unicode font is available. */
    public boolean hasUnicodeFonts() {
        return latin != null || bengali != null || chakma != null;
    }

    /** A font that can render {@code text} at the given size, choosing the face by the text's script. */
    public Font font(String text, float size, boolean bold) {
        int style = bold ? Font.BOLD : Font.NORMAL;
        BaseFont base = switch (scriptOf(text)) {
            case BENGALI -> firstNonNull(bold ? bengaliBold : bengali, bengali, latinBold, latin);
            case CHAKMA -> firstNonNull(chakma, latin);
            case LATIN -> firstNonNull(bold ? latinBold : latin, latin);
        };
        if (base != null) {
            return new Font(base, size, style);
        }
        return FontFactory.getFont(bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, size, style);
    }

    private enum Script { LATIN, BENGALI, CHAKMA }

    private static Script scriptOf(String text) {
        if (text == null) {
            return Script.LATIN;
        }
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 0x0980 && cp <= 0x09FF) {
                return Script.BENGALI;
            }
            if (cp >= 0x11100 && cp <= 0x1114F) {
                return Script.CHAKMA;
            }
        }
        return Script.LATIN;
    }

    private static BaseFont firstNonNull(BaseFont... candidates) {
        for (BaseFont candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static BaseFont load(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                return null;
            }
            byte[] bytes;
            try (InputStream in = resource.getInputStream()) {
                bytes = in.readAllBytes();
            }
            return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, BaseFont.CACHED, bytes, null);
        } catch (Exception e) {
            log.warn("Could not load PDF font {}: {}", path, e.getMessage());
            return null;
        }
    }
}
