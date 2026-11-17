'use strict';
/**
 * DSViz פועל או כבוי — הלחצן האדום "כיבוי DSViz" / הירוק "הפעלת DSViz" (כמו Power ב-Eclipse ו-Dsviz.on() ב-IntelliJ).
 * המצב נשמר ב-globalState (נשאר אחרי סגירת VS Code ואחרי עדכון של התוסף). כשכבוי, המפתח dsviz.off (setContext) מסתיר ב-package.json
 * את כל הכפתורים, התפריטים, הפקודות בלוח הפקודות, קיצורי המקלדת והתצוגה בסרגל הצד; extension.js ו-submit.js לא מציגים 💡 הצעה לפתרון,
 * סימון של סעיפים, הסברים / שגיאות, ולא מוסיפים לבד את ספריית הקורס או את שורת ה-import. שום קובץ של התלמיד לא משתנה ולא נמחק —
 * ספרייה שכבר נוספה לפרויקט נשארת, כדי שהקוד ימשיך להתקמפל ולרוץ כרגיל.
 * המפתח הוא "כבוי" (ולא "פועל"): עד שהתוסף נטען (onStartupFinished) המפתח לא מוגדר — והכפתורים מוצגים כרגיל, בלי להבהב בכל פתיחה.
 */

const KEY = 'dsviz.enabled';
const CONTEXT = 'dsviz.off';

const OFF_TITLE = 'לכבות את DSViz?';
const OFF_DETAIL = 'כל מה ש-DSViz מוסיף ל-VS Code ייכבה:\n'
    + '•  הכפתורים (שליחת תרגיל, תרגיל חדש, הוסף מבנה נתונים, הרצה ויזואלית, הדרכה), התפריט "מבני נתונים" והכפתורים בשורת המצב\n'
    + '•  💡 ההצעות לפתרון מעל הסעיפים, הסימון של סעיפים שעוד לא נענו, וההסברים בעברית לשגיאות\n'
    + '•  ההוספה האוטומטית של ספריית הקורס לתיקיות ושל שורת ה-import של פעולות העזר\n\n'
    + 'יישאר VS Code רגיל בלבד. הקוד והפרויקטים שלכם לא משתנים ולא נמחקים, וממשיכים לרוץ כרגיל (Run) — '
    + 'גם ספריית הקורס שכבר נוספה לפרויקט נשארת במקומה.\n\n'
    + 'כדי להחזיר הכול: הלחצן הירוק "הפעלת DSViz" (למעלה, בקובץ Java או C#), או "DSViz כבוי" בשורת המצב למטה.';
const OFF_BUTTON = 'כיבוי';

/** המצב של DSViz בחלון הזה: on, set(on) — שומר, מעדכן את dsviz.off ומודיע למי שנרשם ב-onChange */
function create(vscode, ctx) {
    const listeners = [];
    const state = {
        get on() { return !ctx.globalState || ctx.globalState.get(KEY) !== false; },
        /** מעדכן את המפתח dsviz.off לפי המצב השמור (בטעינה, ואחרי כל שינוי) */
        sync() { return vscode.commands.executeCommand('setContext', CONTEXT, !state.on); },
        async set(on) {
            await ctx.globalState.update(KEY, !!on);
            await state.sync();
            for (const f of listeners) {
                try { await f(!!on); } catch (e) { /* מאזין אחד שנכשל לא עוצר את השאר */ }
            }
        },
        onChange(f) { listeners.push(f); }
    };
    return state;
}

module.exports = { create, KEY, CONTEXT, OFF_TITLE, OFF_DETAIL, OFF_BUTTON };
