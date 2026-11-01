// מה שמשותף לעמודים: הסרטון (רץ רק כשהוא על המסך), הלשוניות של הגלריה, תאריך הגרסה, ותוכן העניינים במדריך ההתקנה
(function () {
  var calm = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  // הסרטון הקצר רץ לבד רק כשהוא על המסך, ולא כשמבקשים פחות תנועה
  document.querySelectorAll('video[data-autoplay]').forEach(function (v) {
    if (calm || !('IntersectionObserver' in window)) { v.removeAttribute('loop'); return; }
    new IntersectionObserver(function (es) {
      es.forEach(function (e) {
        if (e.isIntersecting) { var p = v.play(); if (p && p.catch) p.catch(function () {}); } else v.pause();
      });
    }, { threshold: 0.5 }).observe(v);
  });

  // לשוניות (כמו בעורך): חיצים, Home / End, והלשונית שנבחרה מוצגת
  document.querySelectorAll('[data-tabs-ui]').forEach(function (box) {
    var tabs = Array.prototype.slice.call(box.querySelectorAll('[role=tab]'));
    function pick(t, focus) {
      tabs.forEach(function (x) {
        var on = x === t;
        x.setAttribute('aria-selected', on ? 'true' : 'false');
        x.tabIndex = on ? 0 : -1;
        var pane = document.getElementById(x.getAttribute('aria-controls'));
        if (pane) pane.setAttribute('aria-hidden', on ? 'false' : 'true');
      });
      if (focus) t.focus();
    }
    tabs.forEach(function (t, i) {
      t.addEventListener('click', function () { pick(t, false); });
      t.addEventListener('keydown', function (e) {
        // סרגל הלשוניות משמאל לימין (שמות קבצים) — ימינה = הבאה
        var k = e.key, j = k === 'ArrowRight' ? i + 1 : k === 'ArrowLeft' ? i - 1 : k === 'Home' ? 0 : k === 'End' ? tabs.length - 1 : null;
        if (j === null) return;
        e.preventDefault();
        pick(tabs[(j + tabs.length) % tabs.length], true);
      });
    });
  });

  // התאריך של הגרסה האחרונה — מאותו קובץ שהעדכון האוטומטי קורא
  var ver = document.querySelector('[data-version]');
  if (ver && window.fetch) {
    fetch('https://raw.githubusercontent.com/motiml77/java-lamerhav/master/java-visualizer/release/version.json', { cache: 'no-store' })
      .then(function (r) { return r.ok ? r.json() : null; })
      .then(function (v) {
        if (!v || !v.build) return;
        var b = String(v.build), months = ['בינואר', 'בפברואר', 'במרץ', 'באפריל', 'במאי', 'ביוני', 'ביולי', 'באוגוסט', 'בספטמבר', 'באוקטובר', 'בנובמבר', 'בדצמבר'];
        var d = +b.slice(6, 8), m = +b.slice(4, 6), y = b.slice(0, 4);
        if (!d || !m) return;
        ver.textContent = 'גרסה ' + v.version + ', עודכנה ב-' + d + ' ' + months[m - 1] + ' ' + y + '.';
      })
      .catch(function () {});
  }

  // מדריך ההתקנה: הסעיף שעל המסך מסומן בתוכן העניינים
  var toc = document.querySelectorAll('.toc a[href^="#"]');
  if (toc.length && 'IntersectionObserver' in window) {
    var map = {};
    toc.forEach(function (a) { map[a.getAttribute('href').slice(1)] = a; });
    var obs = new IntersectionObserver(function (es) {
      es.forEach(function (e) {
        if (!e.isIntersecting) return;
        toc.forEach(function (a) { a.classList.remove('on'); a.removeAttribute('aria-current'); });
        var a = map[e.target.id];
        if (a) { a.classList.add('on'); a.setAttribute('aria-current', 'true'); }
      });
    }, { rootMargin: '-80px 0px -65% 0px' });
    Object.keys(map).forEach(function (id) {
      var el = document.getElementById(id);
      if (el) obs.observe(el);
    });
  }
})();
