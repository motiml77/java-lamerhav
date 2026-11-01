// ההורדות, בכל עמוד באתר:
//  1. ספירה: לחיצה על DSViz-Setup.exe / dsviz-install.zip, והעתקה של פקודת ההתקנה ל-Mac (כפתור "העתקה" או Ctrl+C),
//     שולחות לשרת "נספר" ברקע (sendBeacon / keepalive). ההורדה עצמה ישירות מ-GitHub, בקישור הקבוע, ולא מחכה לשרת.
//  2. המספר: /v1/stats מוצג בכל [data-downloads]; כשהמונה לא מוגדר בשרת (או שהשרת לא עונה) — לא מוצג כלום.
//  3. כפתור ההורדה הראשי לפי מערכת ההפעלה: <html data-os="mac|win|other">. האפשרות השנייה תמיד גלויה. לבדיקה: ?os=mac / ?os=win
(function () {
  var SERVER = 'https://dsviz-exercise.motiml77.workers.dev';

  function count(kind) {
    var url = SERVER + '/v1/count/' + kind;
    try {
      if (navigator.sendBeacon && navigator.sendBeacon(url)) return;
      fetch(url, { method: 'POST', mode: 'no-cors', keepalive: true }).catch(function () {});
    } catch (err) { /* בלי ספירה — ההורדה ממשיכה */ }
  }
  window.dsvizCount = count;

  document.addEventListener('click', function (e) {
    var a = e.target.closest && e.target.closest('a[href]');
    if (!a) return;
    var kind = /DSViz-Setup\.exe$/.test(a.href) ? 'windows' : /dsviz-install\.zip$/.test(a.href) ? 'zip' : null;
    if (kind) count(kind);
  });

  // פקודת ההתקנה ל-Mac: "העתקה" (או סימון והעתקה ידנית) — נספר פעם אחת לכל טעינה של העמוד
  var macCounted = false;
  function macOnce() { if (!macCounted) { macCounted = true; count('mac'); } }
  document.addEventListener('copy', function () {
    var s = window.getSelection && window.getSelection();
    var n = s && s.anchorNode;
    var el = n && (n.nodeType === 1 ? n : n.parentNode);
    if (el && el.closest && el.closest('[data-cmd]')) macOnce();
  });

  function copyText(btn, code) {
    var t = code.textContent.trim();
    var label = btn.getAttribute('data-label') || btn.textContent;
    btn.setAttribute('data-label', label);
    var done = function () {
      btn.textContent = 'הועתק';
      btn.classList.add('done');
      setTimeout(function () { btn.textContent = label; btn.classList.remove('done'); }, 2000);
    };
    var pick = function () { var r = document.createRange(); r.selectNodeContents(code); var s = getSelection(); s.removeAllRanges(); s.addRange(r); };
    macOnce();
    if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(t).then(done, pick);
    else { pick(); try { document.execCommand('copy'); done(); } catch (e) { /* מסומן — מעתיקים לבד */ } }
  }
  document.addEventListener('click', function (e) {
    var b = e.target.closest && e.target.closest('[data-copy-cmd]');
    if (!b) return;
    var box = b.closest('[data-cmd-box]') || b.parentNode;
    var code = box.querySelector('[data-cmd]');
    if (code) copyText(b, code);
  });

  // מערכת ההפעלה
  function detect() {
    var q = /[?&]os=(mac|win|other)\b/.exec(location.search);
    if (q) return q[1];
    var uad = navigator.userAgentData, p = (uad && uad.platform) || navigator.platform || '', ua = navigator.userAgent || '';
    var touch = navigator.maxTouchPoints > 1;
    if (/mac/i.test(p) && !touch) return 'mac';            // iPad מדווח MacIntel — אבל עם מסך מגע
    if (/win/i.test(p) || /Windows/.test(ua)) return 'win';
    return 'other';
  }
  // (בראש כל עמוד יש שורה קצרה שעושה את אותו דבר לפני הציור — כדי שהכפתור לא יקפוץ; כאן — אם היא חסרה)
  if (!document.documentElement.hasAttribute('data-os')) document.documentElement.setAttribute('data-os', detect());

  // מספר ההורדות (Windows + ZIP + העתקות של פקודת ה-Mac)
  var slots = document.querySelectorAll('[data-downloads]');
  if (slots.length && window.fetch) {
    fetch(SERVER + '/v1/stats')
      .then(function (r) { return r.ok ? r.json() : null; })
      .then(function (s) {
        var n = s && s.counting && s.downloads && s.downloads.total;
        if (!n) return;
        var t = n === 1 ? 'הורדה אחת עד היום' : n.toLocaleString('he-IL') + ' הורדות עד היום';
        slots.forEach(function (el) { el.textContent = t; el.hidden = false; });
      })
      .catch(function () {});
  }
})();
