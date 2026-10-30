// ההורדה עצמה ישירות מ-GitHub (עובדת תמיד), ובמקביל השרת מוסיף 1 למונה — בלי לעכב את ההורדה, וגם אם השרת לא זמין
(function () {
  var COUNT = 'https://dsviz-exercise.motiml77.workers.dev/v1/count/';
  document.addEventListener('click', function (e) {
    var a = e.target.closest && e.target.closest('a[href]');
    if (!a) return;
    var kind = /DSViz-Setup\.exe$/.test(a.href) ? 'windows' : /dsviz-install\.zip$/.test(a.href) ? 'zip' : null;
    if (!kind) return;
    try {
      if (navigator.sendBeacon) navigator.sendBeacon(COUNT + kind);
      else fetch(COUNT + kind, { method: 'POST', mode: 'no-cors', keepalive: true });
    } catch (err) { /* בלי ספירה — ההורדה ממשיכה */ }
  });
})();
