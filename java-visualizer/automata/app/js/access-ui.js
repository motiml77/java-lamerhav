/*
 * access-ui.js - הכניסה בממשק: הכפתור של החשבון בסרגל העליון (עם התפריט "עמדת ניהול" / "התנתקות"), ומסך הכניסה של המאגר
 * (כמו AccessGate של Java: מורה / עמדת מחשב בכיתה / תלמיד, "ממשיכים בדפדפן", הודעות, "נכנסת בהצלחה").
 * הלוגיקה והנוסחים ב-access.js. DSVizAccess מגיע מ-js/access-client.js דרך window.DSVizAccessReady (index.html).
 * בלי התוכנה המקומית: אין כפתור חשבון, והמאגר מסביר שהוא עובד בתוכנה המותקנת.
 */
(function (root) {
  'use strict';
  const A = root.AutomataAccess, B = root.AutomataBank, I = root.Icons, D = root.Dialogs, V = root.BankView;
  const { esc, rtl } = V;
  const $ = (s, el) => (el || document).querySelector(s);
  const ROLE = { teacher: 'מורה', student: 'תלמיד', station: 'עמדת מחשב', pending: 'מחכה לכניסה', blocked: 'הגישה הושבתה', 'signed-out': 'לא מחובר' };

  const AccessUI = {
    init(app, ready) {
      this.app = app;
      this.ctl = null;
      this.gate = null;           // { view, retry, busy, notice }
      this.known = new Promise((res) => { this.setKnown = res; });
      this.chip = $('#acct');
      this.chip.addEventListener('click', (e) => this.onChip(e));
      B.setAccess(() => !!this.ctl && this.ctl.allowed(), (action, q, retry) => this.deny(retry));
      Promise.resolve(ready).catch(() => null).then((client) => this.connect(client));
    },

    connect(client) {
      this.ctl = A.controller(client, {
        change: (a) => { this.renderChip(a); if (a) this.setKnown(); },
        gained: (a) => this.gained(a),
        lost: () => this.lost(),
      });
      this.ctl.start();
      if (this.ctl.mode === 'unavailable') this.setKnown();
      else setTimeout(() => this.setKnown(), 4000);   // התוכנה לא ענתה: מסך הכניסה יגיד מה קרה
    },

    /* ---------- הכפתור בסרגל העליון ---------- */
    renderChip(a) {
      const show = !!a && this.ctl.mode === 'ready';
      this.chip.hidden = !show;
      if (!show) return;
      const signed = A.allowed(a);
      const btn = $('.acct-chip', this.chip);
      btn.className = 'btn ghost acct-chip ' + a.state;
      btn.innerHTML = I.get('user', 18) + '<span class="acct-label">' + rtl(A.label(a)) + '</span>' + I.get('chevronDown', 14).replace('class="icon"', 'class="icon chev"');
      btn.title = signed ? (a.email || A.label(a)) + (a.offline ? ' (בלי אינטרנט)' : '') : 'כניסה למאגר השאלות';
      const items = [];
      if (a.state === 'teacher') items.push('<button type="button" role="menuitem" data-ac="panel">' + I.get('sliders', 18) + '<span class="mi-text"><b>עמדת ניהול</b><small>קוד המורה ועמדות המחשב</small></span></button>');
      if (A.canSignOut(a)) items.push('<button type="button" role="menuitem" data-ac="signout">' + I.get('x', 18) + '<span class="mi-text"><b>התנתקות</b><small>בכל התוכנות במחשב הזה</small></span></button>');
      if (!signed) items.push('<button type="button" role="menuitem" data-ac="signin">' + I.get('bank', 18) + '<span class="mi-text"><b>כניסה למאגר השאלות</b><small>מורה, תלמיד או עמדת מחשב</small></span></button>');
      const note = a.state === 'station' ? '<p class="acct-note">את העמדה משחרר המורה מעמדת הניהול.</p>' : a.offline ? '<p class="acct-note">בלי אינטרנט: המאגר פתוח לפי הכניסה השמורה.</p>' : '';
      $('#acct-menu').innerHTML = '<div class="acct-head"><b>' + esc(ROLE[a.state] || '') + '</b>' + (signed ? '<span>' + rtl(a.name || '') + '</span>' + (a.email ? '<small dir="ltr">' + esc(a.email) + '</small>' : '') : '') + '</div>' + note + items.join('');
    },
    async onChip(e) {
      const app = this.app;
      if (e.target.closest('.acct-chip')) { app.toggleMenu('acct-menu', e.target.closest('.acct-chip')); return; }
      const b = e.target.closest('[data-ac]');
      if (!b) return;
      app.toggleMenu('acct-menu', null, false);
      if (b.dataset.ac === 'panel') {
        const r = await this.ctl.openTeacherPanel();
        if (r && !r.ok) app.toast(A.clean(r.message) || 'עמדת הניהול לא נפתחה.', 'err');
        else app.toast('עמדת הניהול נפתחת בחלון של DSViz');
      } else if (b.dataset.ac === 'signout') this.signOut();
      else if (b.dataset.ac === 'signin') this.openGate(() => app.bank.show());
    },
    async signOut() {
      const ask = (q) => D.confirm({ title: q.title, html: q.lines.map((l) => '<p class="ac-q">' + rtl(l) + '</p>').join(''), ok: q.ok, cancel: q.cancel });
      this.signingOut = true;
      const r = await this.ctl.signOut(ask);
      this.signingOut = false;
      if (r) this.app.toast(r.message, r.ok ? 'ok' : 'err');
    },

    /* ---------- שינויים חיים ---------- */
    gained() {
      const app = this.app;
      if (!this.restored) { this.restored = true; app.bankFloat.restore(); }     // תרגיל שהיה פתוח לפני טעינה מחדש
      const g = this.gate;
      if (g && !g.busy && g.view !== 'success') this.show(g.view === 'pay' ? A.paid() : { view: 'success', title: 'נכנסת בהצלחה', text: 'המאגר פתוח.', tone: 'ok' });
    },
    /** התנתקות (כאן, או ב-Eclipse / Visual Studio): המאגר והתרגיל נסגרים, העבודה נשמרת */
    lost() {
      const app = this.app, was = app.bank.isOpen() || app.bankFloat.isOpen();
      app.bank.close();
      app.bankFloat.detach();
      if (was && !this.signingOut) app.toast('התנתקת במחשב הזה. המאגר נסגר.');
    },

    /** אין גישה (AutomataBank.denied): בלי התוכנה מסבירים; אחרת מסך הכניסה, ואחרי כניסה retry */
    async deny(retry) {
      await this.known;
      const c = this.ctl;
      if (!c || c.mode === 'unavailable') { D.confirm({ title: esc(A.T.unavailableTitle), html: '<p class="ac-q">' + rtl(A.T.unavailableText) + '</p>', ok: 'הבנתי', single: true }); return; }
      if (c.allowed()) { retry(); return; }
      this.openGate(retry);
    },

    /* ---------- מסך הכניסה ---------- */
    openGate(retry) {
      const a = this.ctl.access;
      this.gate = { view: 'choose', retry, notice: a && a.state === 'blocked' ? A.T.blocked : !a ? 'התוכנה לא ענתה. סוגרים את החלון ופותחים שוב מהקיצור "מודלים חישוביים".' : '' };
      if (!this.el) {
        this.el = document.createElement('div');
        this.el.className = 'modal-backdrop ac-backdrop';
        this.el.addEventListener('click', (e) => this.onGate(e));
        this.el.addEventListener('keydown', (e) => { e.stopPropagation(); if (e.key === 'Escape') this.closeGate(); if (e.key === 'Enter' && e.target.closest('.ac-field')) this.claim(); });
      }
      document.body.appendChild(this.el);
      this.el.classList.add('fresh');
      setTimeout(() => this.el.classList.remove('fresh'), 400);
      if (a && a.state === 'pending' && a.signing) this.show({ view: 'wait' });
      else if (a && a.state === 'pending') this.show(A.payment());
      else this.show({ view: 'choose' });
    },
    closeGate() {
      if (!this.gate) return;
      if (this.gate.view === 'wait') this.ctl.cancel();
      clearTimeout(this.gate.timer);
      this.gate = null;
      this.el.remove();
    },
    isOpen() { return !!this.gate; },

    show(v) {
      const g = this.gate;
      if (!g) return;
      g.view = v.view;
      const T = A.T, I18 = (n) => I.get(n, 18);
      let body;
      if (v.view === 'choose') {
        body = '<h2 id="ac-t">' + T.title + '</h2>' + (g.notice ? '<p class="ac-notice">' + rtl(g.notice) + '</p>' : '') + '<p class="ac-sub">' + rtl(T.sub) + '</p>' +
          '<div class="ac-cards">' + T.roles.map((r) => '<section class="ac-card"><h3>' + r.title + '</h3><p>' + rtl(r.text) + '</p><button type="button" class="btn primary" data-ac-role="' + r.id + '">' + rtl(r.action) + '</button></section>').join('') + '</div>';
      } else if (v.view === 'station') {
        body = '<h2 id="ac-t">' + T.stationTitle + '</h2><p class="ac-sub">' + rtl(T.stationText) + '</p>' +
          '<label class="ac-label" for="ac-code">' + T.code + '</label><input id="ac-code" class="ac-field ac-code" dir="ltr" maxlength="9" autocomplete="off" spellcheck="false" placeholder="K7QM">' +
          '<label class="ac-label" for="ac-name">' + T.name + '</label><input id="ac-name" class="ac-field" dir="auto" maxlength="80" autocomplete="off" placeholder="מעבדה 2 · עמדה 14">' +
          '<p class="ac-error" role="alert"></p><div class="ac-row"><button type="button" class="btn primary" data-ac-go="claim">' + T.connect + '</button><button type="button" class="btn secondary" data-ac-go="choose">' + T.back + '</button></div>';
      } else if (v.view === 'wait') {
        body = '<h2 id="ac-t">' + T.waitTitle + '</h2><p class="ac-sub big">' + rtl(T.waitText) + '</p><div class="ac-spin" aria-hidden="true"></div><div class="ac-row"><button type="button" class="btn secondary" data-ac-go="cancel">' + T.cancel + '</button></div>';
      } else {
        body = '<div class="ac-box ' + (v.tone || 'warn') + '" role="status"><h2 id="ac-t">' + rtl(v.title) + '</h2><p>' + rtl(v.text) + '</p></div>' +
          (v.view === 'success' ? '' : '<div class="ac-row"><button type="button" class="btn secondary" data-ac-go="choose">' + T.back + '</button></div>');
      }
      this.el.innerHTML = '<div class="ac-window" role="dialog" aria-modal="true" aria-labelledby="ac-t"><header class="ac-head"><span class="ac-logo">' + I18('bank') + '</span><b>מאגר שאלות</b>' +
        '<button type="button" class="btn icon ghost sm" data-ac-go="close" title="סגירה (Esc)" aria-label="סגירה">' + I.get('x', 16) + '</button></header><div class="ac-body ' + v.view + '">' + body + '</div></div>';
      const f = this.el.querySelector(v.view === 'station' ? '#ac-code' : '.ac-body .btn');
      if (f) f.focus();
      if (v.view === 'success') g.timer = setTimeout(() => { const retry = g.retry; if (this.gate !== g) return; this.closeGate(); if (retry) retry(); }, 1400);
    },

    async onGate(e) {
      const g = this.gate;
      if (!g) return;
      if (e.target === this.el) { this.closeGate(); return; }
      const role = e.target.closest('[data-ac-role]');
      if (role) {
        const r = role.dataset.acRole;
        if (r === 'station') { this.show({ view: 'station' }); return; }
        this.show({ view: 'wait' });
        g.busy = true;
        const v = await this.ctl.signIn(r);
        g.busy = false;
        if (this.gate === g && g.view === 'wait') this.show(v);
        return;
      }
      const go = e.target.closest('[data-ac-go]');
      if (!go) return;
      const k = go.dataset.acGo;
      if (k === 'close') this.closeGate();
      else if (k === 'choose') this.show({ view: 'choose' });
      else if (k === 'cancel') { this.ctl.cancel(); this.show({ view: 'choose' }); }
      else if (k === 'claim') this.claim();
    },
    async claim() {
      const g = this.gate;
      if (!g || g.view !== 'station' || g.busy) return;
      const btn = this.el.querySelector('[data-ac-go=claim]'), err = this.el.querySelector('.ac-error');
      const code = this.el.querySelector('#ac-code').value, name = this.el.querySelector('#ac-name').value;
      const bad = A.stationInput(code, name);
      if (bad) { err.innerHTML = rtl(bad); return; }
      err.textContent = '';
      btn.disabled = true; btn.textContent = A.T.connecting; g.busy = true;
      const v = await this.ctl.station(code, name);
      g.busy = false;
      if (this.gate !== g) return;
      if (v.view === 'inline') { btn.disabled = false; btn.textContent = A.T.connect; err.innerHTML = rtl(v.text); err.classList.toggle('long', v.text.length > 70); return; }
      this.show(v);
    },
  };

  root.AccessUI = AccessUI;
})(typeof globalThis !== 'undefined' ? globalThis : this);
