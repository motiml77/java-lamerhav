// ההרשמה של תלמיד (פעם אחת): שם מלא וטלפון נייד, ובפעם הראשונה גם הבחירה "תלמיד עצמאי" / "יש לי קוד מורה".
// משותף ל-/signin (אחרי הכניסה עם Google, לפני החזרה לתוכנה), ל-/welcome?r=register (תלמיד שכבר מחובר בתוכנה ועוד לא נרשם)
// ול-/account (עדכון הפרטים). השרת בודק שוב את הכול (POST /v1/student/register, /v1/student/join, /v1/student/mode).
import { api, h, busy, ask } from '/static/common.js';

/** טלפון נייד: 10 ספרות שמתחילות ב-05 (מקבל גם רווחים, מקפים ו-‎+972). מחזיר את 10 הספרות או '' */
export function phoneNorm(v) {
  let d = String(v == null ? '' : v).replace(/[\s\-().]/g, '');
  if (/^\+?972/.test(d)) d = '0' + d.replace(/^\+?972/, '');
  return /^05\d{8}$/.test(d) ? d : '';
}

/** שם מלא: לפחות שתי מילים של שתי אותיות ומעלה (כמו בשרת — fullNameNorm). מחזיר את השם מסודר או '' */
export function fullNameNorm(v) {
  const s = String(v == null ? '' : v).replace(/\s+/g, ' ').trim();
  if (s.length < 5 || s.length > 60) return '';
  if (!/^[\p{L}\p{M} .'׳״’"-]+$/u.test(s)) return '';
  return s.split(' ').filter(w => (w.match(/\p{L}/gu) || []).length >= 2).length >= 2 ? s : '';
}

/** מה חסר לתלמיד: reg — שם מלא וטלפון; mode — עוד לא בחר "עצמאי / מורה" (חשבון של מורה לא נשאל על מורה) */
export function missing(me) {
  const st = me && me.student || {};
  return { reg: !st.registered, mode: !me.teacher && !!st.state && st.state.mode === '' };
}

const JOIN_ERRORS = {
  'bad-code': 'הקוד לא נכון. בודקים אותו מול המורה (4 תווים: אותיות באנגלית וספרות).',
  closed: 'המורה סגר כרגע את ההצטרפות. פונים למורה.',
  blocked: 'המורה הזה לא פעיל כרגע.',
};

/**
 * טופס ההרשמה. opts: me (התשובה של /v1/me), parts {reg, mode} — מה להציג, submit — הכיתוב על הכפתור,
 * onDone({fullName, teacher}) — אחרי שהכול נשמר (teacher: {name, school} או null).
 */
export function registerForm({ me, parts, submit = 'סיום ההרשמה', onDone }) {
  const st = me.student || {};
  const guess = fullNameNorm(me.name) && !/\d/.test(me.name) ? fullNameNorm(me.name) : '';
  const name = h('input', { class: 'field', type: 'text', id: 'reg-name', autocomplete: 'name', maxlength: '60', placeholder: 'שם פרטי ושם משפחה', value: st.fullName || guess });
  const phone = h('input', { class: 'field ltr', type: 'tel', id: 'reg-phone', inputmode: 'numeric', autocomplete: 'tel', maxlength: '20', placeholder: '050-1234567', value: st.phone || '', style: { textAlign: 'left' } });
  const code = h('input', { class: 'field mono ltr', type: 'text', id: 'reg-code', maxlength: '6', placeholder: 'W8S2', autocomplete: 'off', autocapitalize: 'characters', spellcheck: 'false', 'aria-label': 'קוד המורה', style: { width: '130px', textAlign: 'center' } });
  const err = h('p', { role: 'alert', class: 'warn-box', hidden: true });
  const fail = (msg, field) => { err.textContent = msg; err.hidden = false; if (field) field.focus(); };
  const go = h('button', { type: 'submit', class: 'btn primary lg wide' }, submit);

  let mode = '';
  const codeRow = h('div', { class: 'stack', hidden: true, style: { gap: '6px' } },
    h('label', { class: 'lbl', for: 'reg-code' }, 'קוד המורה (4 תווים)', code),
    h('p', { class: 'small muted' }, 'המורה יראה את השם שלך, את המייל ואת התרגילים שתגיש לו.'));
  const pick = (value, title, text) => {
    const radio = h('input', { type: 'radio', name: 'reg-mode', value, id: 'reg-mode-' + value });
    radio.addEventListener('change', () => {
      mode = value;
      codeRow.hidden = value !== 'teacher';
      if (value === 'teacher') code.focus();
    });
    return h('label', { class: 'choice', for: 'reg-mode-' + value }, radio,
      h('span', { class: 'stack', style: { gap: '2px' } }, h('b', null, title), h('span', { class: 'small muted' }, text)));
  };
  const modeBox = parts.mode ? h('fieldset', { class: 'choices' },
    h('legend', { class: 'lbl' }, 'איך לומדים?'),
    pick('independent', 'תלמיד עצמאי', 'לומדים לבד. אפשר להצטרף למורה גם אחר כך.'),
    pick('teacher', 'יש לי קוד מורה', 'מצטרפים למורה, ואפשר להגיש לו תרגילים.'),
    codeRow) : null;

  async function join(replace) {
    const r = await api('POST', '/v1/student/join', { code: code.value, replace: !!replace });
    if (r.status === 'replace') {
      if (await ask({ title: `לעבור למורה ${r.teacher.name}?`, text: 'אפשר להיות משויך למורה אחד. המורה הקודם יראה אותך כ"עזב", והתרגילים שהגשת לו נשארים אצלו.', ok: 'מעבר' })) return join(true);
      return null;
    }
    if (JOIN_ERRORS[r.status]) { fail(JOIN_ERRORS[r.status], code); return null; }
    return r.teacher || { name: '', school: '' };
  }

  const form = h('form', { class: 'stack', novalidate: '', style: { gap: '14px', width: '100%', textAlign: 'right' } },
    parts.reg ? h('label', { class: 'lbl', for: 'reg-name' }, 'שם מלא', name) : null,
    parts.reg ? h('label', { class: 'lbl', for: 'reg-phone' }, 'טלפון נייד (שלך או של הורה)', phone) : null,
    modeBox, err, go);
  form.addEventListener('submit', e => {
    e.preventDefault();
    err.hidden = true;
    const fullName = fullNameNorm(name.value), ph = phoneNorm(phone.value);
    if (parts.reg && !fullName) return fail('כותבים שם פרטי ושם משפחה (אותיות בלבד, למשל: נועה כהן).', name);
    if (parts.reg && !ph) return fail('כותבים נייד תקני של 10 ספרות, למשל 050-1234567.', phone);
    if (parts.mode && !mode) return fail('בוחרים: תלמיד עצמאי, או "יש לי קוד מורה".');
    if (parts.mode && mode === 'teacher' && code.value.trim().length < 4) return fail('כותבים את קוד המורה (4 תווים).', code);
    return busy(go, async () => {
      try {
        if (parts.reg) await api('POST', '/v1/student/register', { fullName, phone: ph });
        let teacher = null;
        if (parts.mode && mode === 'teacher') {
          teacher = await join(false);
          if (!teacher) return;
        } else if (parts.mode) await api('POST', '/v1/student/mode', { mode: 'independent' });
        await onDone({ fullName: fullName || st.fullName || '', teacher });
      } catch (x) {
        fail(x.message || 'משהו השתבש. נסו שוב.');   // ההודעה נשארת מול הטופס (ולא רק בהודעה קופצת)
      }
    });
  });
  return form;
}

/** 10 התווים הראשונים של SHA-256 של המייל — כך התוכנה מוודאת שבדפדפן מחובר אותו חשבון, בלי לשים את המייל בכתובת */
export async function emailMark(email) {
  const data = new TextEncoder().encode(String(email || '').trim().toLowerCase());
  const hash = await crypto.subtle.digest('SHA-256', data);
  return Array.from(new Uint8Array(hash)).map(b => b.toString(16).padStart(2, '0')).join('').slice(0, 10);
}
