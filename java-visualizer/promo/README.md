# סרטון פרומו — DSViz

הסרטון המוכן: [`../docs/promo.mp4`](../docs/promo.mp4) (‏43 שניות, 1920x1080, עם פסקול).

**מבנה:** פתיחה (כותבים פונקציה → לוחצים הרצה → הקוד שלכם בתנועה) · 01 מחסנית · 02 עץ בינארי · 03 רקורסיה · סיום (Eclipse / VS Code, התקנה אחת).
כל מה שרואים בתוך החלון הוא צילומי מסך אמיתיים של חלון ההרצה, צעד אחר צעד.

## איך לייצר מחדש (Linux, צריך Xvfb, Java, Node + Playwright, ffmpeg, Python עם numpy ו-Pillow)

```sh
cd java-visualizer/promo
# 1. צילום הצעדים (NX/NY = מיקום הכפתור "הבא" במסך 1600x900)
MAIN=PromoStack NX=1360 NY=66 ./run.sh s $(seq 1 26)
MAIN=PromoTree  NX=1360 NY=66 ./run.sh t $(seq 1 44)
MAIN=PromoRec   NX=1360 NY=66 ./run.sh r $(seq 1 19)
python3 crop.py cap frames                 # חיתוך החלון בלבד

# 2. גופנים (Karantina, Secular One, Suez One, Heebo, Rubik Mono One, JetBrains Mono)
mkdir -p fonts && (cd fonts && npm init -y && npm i @expo-google-fonts/karantina @expo-google-fonts/secular-one \
  @expo-google-fonts/suez-one @expo-google-fonts/heebo @expo-google-fonts/rubik-mono-one @expo-google-fonts/jetbrains-mono)

# 3. רינדור, פסקול וקידוד
mkdir -p out && node shoot.js out 30
python3 music.py music.wav
ffmpeg -framerate 30 -i out/%05d.jpg -i music.wav -c:v libx264 -crf 20 -pix_fmt yuv420p -c:a aac -shortest promo.mp4
```

`promo.html` הוא ציר הזמן כולו (פונקציה `render(t)`); `frames.js` קובע אילו צעדים מוצגים בכל סצנה.
הפסקול סינתטי לגמרי (`music.py`) — אפשר להחליף במוזיקה אחרת עם רישיון מתאים.
