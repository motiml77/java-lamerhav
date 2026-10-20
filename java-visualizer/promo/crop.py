import glob, os, sys
from PIL import Image

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
for f in sorted(glob.glob(os.path.join(src, "*.png"))):
    im = Image.open(f).convert("RGB").crop((80, 36, 1520, 864))   # החלון בלבד (1440x828)
    im.save(os.path.join(out, os.path.basename(f)[:-4] + ".jpg"), quality=93)
print("ok")
