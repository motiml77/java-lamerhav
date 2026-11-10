/*
 * app-run.js - הרצה: התחלה (עם בדיקת תקינות), צעדים, ניגון, ומה מודגש על הקנבס. מרחיב את App.
 */
(function (root) {
  'use strict';
  const M = root.AutomataModel, S = root.AutomataSim;
  const SPEEDS = [0, 1400, 900, 600, 340, 160];   // אלפיות שנייה בין צעדים, לפי המהירות 1..5
  const app = root.App;

  Object.assign(app, {
    isRunning() { return !!(this.run && this.run.trace); },

    startRun() {
      this.popover.close();
      if (this.canvas.source) { this.canvas.source = null; this.hint(null); }
      const m = this.machine;
      const errs = this.issues.filter((i) => i.level === 'error');
      if (errs.length) {
        this.run = { trace: null, index: 0, error: { title: 'יש לתקן את המכונה', text: errs[0].text + (errs.length > 1 ? ' (ועוד ' + (errs.length - 1) + ')' : '') } };
        this.side.show('issues');
        this.select(errs[0].states, errs[0].transitions, true);
        this.runPanel.render();
        return false;
      }
      const bad = M.checkWord(m, this.word);
      if (bad) {
        this.run = { trace: null, index: 0, error: { title: 'אות לא באלפבית', text: bad.text, index: bad.index } };
        this.runPanel.render();
        return false;
      }
      const trace = S.run(m, this.word);
      this.run = { trace, index: 0, playing: false };
      this.sel = { states: new Set(), trans: new Set() };
      this.side.render();
      this.hint(null);
      this.canvas.closeEditor();
      return true;
    },

    stopRun(silent) {
      if (this.run && this.run.timer) clearTimeout(this.run.timer);
      const had = !!this.run;
      this.run = null;
      this.runView = null;
      if (had && !silent) this.refresh();
    },

    goto(i) {
      const r = this.run;
      if (!r || !r.trace) return;
      const last = r.trace.steps.length - 1;
      const ni = Math.max(0, Math.min(last, i));
      const forward = ni === r.index + 1;
      r.index = ni;
      this.updateRunView(forward);
      this.canvas.render();
      this.runPanel.render();
      this.canvas.positionToolbar();
    },

    updateRunView(animate) {
      const r = this.run;
      if (!r || !r.trace) { this.runView = null; return; }
      const tr = r.trace, st = tr.steps[r.index];
      const last = r.index === tr.steps.length - 1;
      const interval = SPEEDS[this.speed] || 600;
      this.runView = {
        active: st ? st.active : [],
        edges: st && r.index > 0 ? st.edges : (st ? st.edges : []),
        accepted: last && tr.result === 'accept' ? tr.acceptedStates : [],
        rejected: last && tr.result !== 'accept' ? (st ? st.active : []) : [],
        animate: !!animate,
        travelMs: Math.min(420, Math.round(interval * 0.7)),
      };
    },

    runCmd(cmd) {
      const r = this.run;
      if (cmd === 'play-restart') { this.stopRun(true); if (this.startRun()) { this.goto(0); this.play(); } return; }
      if (cmd === 'play') {
        if (r && r.playing) { this.pause(); return; }
        if (!r || !r.trace) { if (!this.startRun()) return; this.goto(0); }
        else if (r.index >= r.trace.steps.length - 1) this.goto(0);
        this.play();
        return;
      }
      if (cmd === 'next') { if (!r || !r.trace) { if (this.startRun()) this.goto(0); return; } this.pause(); this.goto(r.index + 1); return; }
      if (cmd === 'end') { if (!r || !r.trace) { if (!this.startRun()) return; } this.pause(); this.goto(this.run.trace.steps.length - 1); return; }
      if (!r || !r.trace) return;
      if (cmd === 'prev') { this.pause(); this.goto(r.index - 1); }
      else if (cmd === 'rewind') { this.pause(); this.goto(0); }
    },

    play() {
      const r = this.run;
      if (!r || !r.trace) return;
      r.playing = true;
      this.runPanel.render();
      const tick = () => {
        if (!this.run || !this.run.playing) return;
        if (this.run.index >= this.run.trace.steps.length - 1) { this.run.playing = false; this.runPanel.render(); return; }
        this.goto(this.run.index + 1);
        this.run.timer = setTimeout(tick, SPEEDS[this.speed] || 600);
      };
      r.timer = setTimeout(tick, Math.min(500, SPEEDS[this.speed] || 600));
    },
    pause() { const r = this.run; if (r && r.playing) { r.playing = false; clearTimeout(r.timer); this.runPanel.render(); } }
  });
})(typeof globalThis !== 'undefined' ? globalThis : this);
