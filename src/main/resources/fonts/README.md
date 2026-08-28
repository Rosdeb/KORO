# PDF export fonts

Drop these TrueType files into this folder to enable embedded Unicode rendering in
the "My Books" PDF export. They are **not** committed to the repo (binary, ~1–2 MB total).

| File | Script | Source |
|---|---|---|
| `NotoSans-Regular.ttf` | Latin | https://fonts.google.com/noto/specimen/Noto+Sans |
| `NotoSans-Bold.ttf` | Latin (bold) | same |
| `NotoSansBengali-Regular.ttf` | Bangla | https://fonts.google.com/noto/specimen/Noto+Sans+Bengali |
| `NotoSansBengali-Bold.ttf` | Bangla (bold) | same |
| `NotoSansChakma-Regular.ttf` | Chakma | https://fonts.google.com/noto/specimen/Noto+Sans+Chakma |

All are licensed under the SIL Open Font License 1.1.

`PdfFontProvider` loads whatever is present and picks a face per script at render time.
If a file is missing, that script falls back to Helvetica (Latin works; Bangla/Chakma
come out as blank boxes) and a warning is logged at startup.

Download example:

```bash
cd src/main/resources/fonts
curl -L -o NotoSansBengali-Regular.ttf \
  "https://github.com/notofonts/notofonts.github.io/raw/main/fonts/NotoSansBengali/unhinted/ttf/NotoSansBengali-Regular.ttf"
```
