// הדרכה ו ל-Visual Studio: "Visual Studio מההתחלה — צעד אחר צעד" — מצילומי מסך אמיתיים בלבד (toolbar-e2e.ps1, סעיף 9, על Windows אמיתי).
// כל שורה: לאיזה צילום (לפי תחילת השם) — ומה כתוב בכיתוב. **מודגש** = מה לוחצים / מה כותבים.
// group: צילומים רצופים עם אותו כיתוב (שורות קוד / "הבא") — כיתוב אחד, צעד אחד. dur: כמה שניות לכל צילום בקבוצה.
module.exports = {
  intro: { title: 'Visual Studio מההתחלה', sub: 'צעד אחר צעד: פרויקט חדש, קוד עם תור, הרצה ויזואלית ומאגר השאלות', chip: 'לא צריך שום ידע מוקדם' },
  outro: { title: 'זהו — אפשר להתחיל לתרגל', sub: 'בכל פרויקט C# רגיל: כותבים קוד ולוחצים "הרצה ויזואלית". עוד הדרכות — בכפתור "הדרכה" בסרגל הכלים.' },
  items: [
    { match: 'tut-01-vs-start', text: 'פותחים את Visual Studio. במסך הראשון לוחצים על **Create a new project** — יצירת פרויקט חדש.' },
    { match: 'tut-02-templates', text: 'נפתחת רשימה של סוגי פרויקטים. לוחצים על תיבת החיפוש שלמעלה.' },
    { match: 'tut-03-console-app', text: 'מקלידים **Console App**, ובוחרים את **Console App** של C# — לא את זה שכתוב בו Framework.' },
    { match: 'tut-03b-next', text: 'לוחצים **Next**.' },
    { match: 'tut-04-project-name', text: 'כותבים שם לפרויקט — למשל **Targil1**. אפשר כל שם באנגלית, בלי רווחים.', kind: 'type' },
    { match: 'tut-04b-next', text: 'ושוב **Next**.' },
    { match: 'tut-05-framework', text: 'במסך הזה לא משנים כלום. לוחצים **Create**.' },
    { match: 'tut-06-project-and-toolbar', text: 'הפרויקט נפתח. למעלה מופיע סרגל הכלים של DSViz — הוא מופיע לבד, בכל פרויקט, בלי להתקין או לבחור עוד משהו.' },
    { match: 'tut-07-default-code', text: 'בקובץ Program.cs יש קוד לדוגמה. מוחקים אותו: **Ctrl+A** ואחר כך **Delete**.', useChange: false },
    { match: 'tut-07a-empty', text: 'הקובץ ריק. עכשיו כותבים את הקוד שלנו.', kind: 'type', noZoom: true },
    { match: /^tut-07-code-\d/, text: 'כותבים קוד עם תור: יוצרים תור, מכניסים אליו **5**, **8** ו-**3**, ומוציאים את כולם כדי לחשב את הסכום.', kind: 'type', group: true, dur: 0.95 },
    { match: 'tut-07-code-done', text: 'שימו לב: אין קו אדום מתחת ל-Queue, ולא צריך להוסיף שום שורה בראש הקובץ — DSViz מוסיף את מה שצריך לבד.', useChange: false, focusFrom: /^tut-07-code-\d/ },
    { match: 'tut-08-run-button', text: 'לוחצים על **הרצה ויזואלית** בסרגל הכלים.' },
    { match: 'tut-08-v00', text: 'נפתח חלון ההרצה הוויזואלית: בצד אחד הקוד, ובצד השני התור עצמו. לוחצים **הבא** כדי להתקדם שורה אחרי שורה.', noZoom: true },
    { match: /^tut-08-v0[1-7]/, text: 'בכל לחיצה על **הבא** רואים מה קרה: איבר נכנס לתור מאחור, איבר יוצא מקדימה — והסכום גדל.', group: true, dur: 1.9, noZoom: true },
    { match: 'tut-08-v-end', text: 'כך רואים כל שלב בריצה של הקוד. כשמסיימים — סוגרים את החלון.', noZoom: true },
    { match: 'tut-09-bank-button', text: 'עכשיו מאגר השאלות: לוחצים על **מאגר שאלות** בסרגל הכלים.' },
    { match: 'tut-10-login-screen', text: 'בפעם הראשונה מופיע מסך כניסה. מורה לוחץ **כניסה עם Google** בכרטיס "מורה". (תלמיד — בכרטיס "תלמיד"; מחשב בכיתה — "הקלדת קוד המורה".)', maxZoom: 1.25 },
    { match: 'tut-10-teacher-waiting', text: 'נפתח הדפדפן. החלון הזה מחכה, וממשיך לבד כשמסיימים להיכנס בדפדפן.', maxZoom: 1.25 },
    { match: 'tut-10-google-page', text: 'בדפדפן נכנסים עם חשבון Google — כמו בכל אתר. את הסיסמה מקלידים רק אצל Google. אחר כך חוזרים ל-Visual Studio.' },
    { match: 'tut-11-bank-window', text: 'אחרי הכניסה נפתח מאגר השאלות: הנושאים בצד, ובכל נושא — שאלות.', noZoom: true },
    { match: 'tut-11-selected', text: 'בוחרים שאלה, קוראים אותה, ולוחצים **צור תרגיל**.', maxZoom: 1.3 },
    { match: 'tut-11-float', text: 'נוצר פרויקט עם הקוד של סעיף א\', ולידו החלון "השאלה". כשמסיימים את סעיף א\' — לוחצים **הכן סעיף ב\'**.', maxZoom: 1.3 },
    { match: 'tut-11-prepared', text: 'סעיף ב\' נוסף לקוד. כותבים את הפתרון, ולוחצים **הרצה ויזואלית** — בדיוק כמו קודם.', noZoom: true },
    { match: 'tut-12-exercise-in-vs', skip: true },
  ],
};
