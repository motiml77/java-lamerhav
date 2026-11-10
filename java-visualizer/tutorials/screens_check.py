# בדיקה לפני שבונים סרטון מצילומים אמיתיים: מציירים על כל צילום את המלבן שנרשם ב-steps.json (לאן לוחצים),
# וגם מקטינים לדף אחד — כדי לראות בעיניים שכל מלבן יושב בדיוק על הכפתור (ושאין חלון שמכסה אותו).
#   python3 screens_check.py <תיקיית הצילומים עם steps.json> <תיקיית פלט>
import json, os, sys
from PIL import Image, ImageDraw

d, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
info = json.load(open(os.path.join(d, 'steps.json'), encoding='utf-8-sig'))
steps = info['steps'] if isinstance(info, dict) else info
thumbs = []
for s in steps:
    p = os.path.join(d, s['shot'])
    if not os.path.exists(p):
        print('חסר', s['shot']); continue
    im = Image.open(p).convert('RGB')
    t = s.get('target')
    dr = ImageDraw.Draw(im)
    if t:
        x, y, w, h = t['x'], t['y'], t['w'], t['h']
        off = x < 0 or y < 0 or x + w > im.width or y + h > im.height or w < 4 or h < 4
        for k in range(4):
            dr.rectangle([x - k, y - k, x + w + k, y + h + k], outline=(255, 0, 255) if off else (255, 200, 0))
        print(f"{s['shot']:<40} {s.get('kind',''):<6} {x},{y} {w}x{h}" + ('  <-- מחוץ למסך / קטן מדי' if off else ''))
    else:
        print(f"{s['shot']:<40} {s.get('kind',''):<6} (בלי מלבן)")
    dr.rectangle([0, 0, im.width, 40], fill=(0, 0, 0))
    dr.text((10, 10), s['shot'] + '  ' + s.get('kind', ''), fill=(255, 255, 255))
    im.save(os.path.join(out, s['shot']))
    thumbs.append(im.resize((im.width // 4, im.height // 4)))
if thumbs:
    cols = 4; tw, th = thumbs[0].size
    rows = (len(thumbs) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * tw, rows * th), (30, 30, 30))
    for i, t in enumerate(thumbs):
        sheet.paste(t, ((i % cols) * tw, (i // cols) * th))
    sheet.save(os.path.join(out, '_sheet.jpg'), quality=85)
print('check ok', len(thumbs), '->', out)
