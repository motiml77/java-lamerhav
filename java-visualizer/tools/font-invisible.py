#!/usr/bin/env python3
"""
מוסיף לגופן (TTF) תווי כיוון בלתי נראים ברוחב אפס: LRE/RLE/PDF/LRO/RLO (U+202A-202E), LRI/RLI/FSI/PDI (U+2066-2069),
LRM/RLM, ZWSP, ZWNJ/ZWJ, WJ, ALM, BOM — וגליף קיים שמצויר (Heebo מצייר את LRM / RLM) מרוקן. הטקסטים בחלונות (מעקב משתנים, תגיות) עוטפים קוד בתווים האלה כדי שיוצג משמאל לימין בתוך
עברית; גופן שאין בו את התווים (Heebo, Chivo Mono) — Java מציירת במקומם סימן נראה. הרישיון (OFL) מתיר שינוי, ואין לגופנים
האלה "Reserved Font Name". דורש fontTools:  python3 tools/font-invisible.py third-party/fonts/Heebo-Regular.ttf ...
"""
import sys
from fontTools.ttLib import TTFont
from fontTools.pens.ttGlyphPen import TTGlyphPen

CPS = [0x061C, 0x200B, 0x200E, 0x200F, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E, 0x2060, 0x2066, 0x2067, 0x2068, 0x2069, 0xFEFF]

for path in sys.argv[1:]:
    f = TTFont(path)
    best = f.getBestCmap()
    glyf = f['glyf']
    added = []
    for cp in CPS + [0x200C, 0x200D]:
        if cp in best:
            # יש כבר גליף — אבל בגופנים מסוימים (Heebo) הוא מצויר (רוחב אפס, ועדיין נראה על המסך): מרוקנים אותו
            name = best[cp]
            if glyf[name].numberOfContours != 0 or f['hmtx'][name][0] != 0:
                glyf[name] = TTGlyphPen(None).glyph()
                f['hmtx'][name] = (0, 0)
                added.append('U+%04X(blanked)' % cp)
            continue
        name = 'uni%04X' % cp
        if name not in glyf.glyphs:
            glyf[name] = TTGlyphPen(None).glyph()   # גליף ריק (נוסף גם לסדר הגליפים של הגופן)
            f['hmtx'][name] = (0, 0)                # רוחב אפס
        for t in f['cmap'].tables:
            if t.isUnicode():
                t.cmap[cp] = name
        added.append('U+%04X' % cp)
    f.setGlyphOrder(glyf.glyphOrder)
    f.save(path)
    print(path, 'added', len(added), ' '.join(added))
