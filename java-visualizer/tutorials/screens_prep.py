# הכנה לסרטון מצילומים אמיתיים (screens.js): לכל צעד בלי מקום ללחוץ (הקלדה / מסתכלים) — איפה השתנה המסך מהצילום הקודם.
# כך המצלמה מתקרבת לשורות הקוד שנכתבו, גם בלי לדעת מראש איפה העורך.
#   python3 screens_prep.py <תיקיית הצילומים עם steps.json> <out.json>
import json, os, sys
import numpy as np
from PIL import Image

d = sys.argv[1]
info = json.load(open(os.path.join(d, 'steps.json'), encoding='utf-8-sig'))
steps = info['steps'] if isinstance(info, dict) else info
prev = None
out = []
for s in steps:
    img = np.asarray(Image.open(os.path.join(d, s['shot'])).convert('L'), dtype=np.int16)
    change = None
    if prev is not None and prev.shape == img.shape:
        diff = np.abs(img - prev) > 28
        # רעש קטן (סמן מהבהב, שעון) לא נחשב: רק שורות / עמודות עם כמה פיקסלים שהשתנו
        ys, xs = np.nonzero(diff)
        if len(xs) >= 40:
            # בלי קצוות רחוקים (שורת המצב, מספר השורה, פס הגלילה): 2%–98% מהפיקסלים שהשתנו
            x1, x2 = np.percentile(xs, [2, 98]); y1, y2 = np.percentile(ys, [2, 98])
            change = {'x': int(x1), 'y': int(y1), 'w': int(x2 - x1 + 1), 'h': int(y2 - y1 + 1), 'frac': float(diff.mean())}
    out.append({'shot': s['shot'], 'change': change})
    prev = img
json.dump({'size': [int(prev.shape[1]), int(prev.shape[0])] if prev is not None else [1920, 1080], 'steps': out}, open(sys.argv[2], 'w'))
print('prep ok', len(out))
