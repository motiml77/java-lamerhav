/*
 * app-ui.js - הסרגל העליון, תפריטים, קבצים (שמירה, פתיחה, ייצוא), ערכת נושא ומקלדת. מרחיב את App.
 */
(function (root) {
  'use strict';
  const M = root.AutomataModel, I = root.Icons, EX = root.AutomataExamples, D = root.Dialogs;
  const THEME_KEY = 'dsviz.automata.theme';
  const $ = (s, el) => (el || document).querySelector(s);
  const $$ = (s, el) => Array.from((el || document).querySelectorAll(s));
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
  const app = root.App;

  Object.assign(app, {
    /* ---------- קבצים ---------- */
    saveFile() {
      const m = this.machine;
      const blob = new Blob([M.serialize(m)], { type: 'application/json' });
      const name = (m.name || 'מכונה').replace(/[\\/:*?"<>|]+/g, '_') + '.automaton.json';
      this.download(blob, name);
      this.toast('נשמר: ' + name, 'ok');
    },
    exportSVG() {
      const svg = this.canvas.exportSVG();
      if (!svg) { this.toast('אין מה לייצא: הקנבס ריק'); return; }
      const name = (this.machine.name || 'מכונה').replace(/[\\/:*?"<>|]+/g, '_') + '.svg';
      this.download(new Blob([svg], { type: 'image/svg+xml' }), name);
      this.toast('יוצא: ' + name, 'ok');
    },
    download(blob, name) {
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = name;
      document.body.appendChild(a);
      a.click();
      setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
    },
    openFile(file) {
      const fr = new FileReader();
      fr.onload = () => {
        const { machine, errors } = M.deserialize(String(fr.result));
        if (!machine) { this.toast('לא ניתן לפתוח: ' + errors[0], 'err'); return; }
        this.loadMachine(machine, 'פתיחת קובץ');
        this.toast(errors.length ? 'נפתח, עם ' + errors.length + ' פריטים שדולגו' : 'נפתח: ' + machine.name, errors.length ? 'err' : 'ok');
      };
      fr.onerror = () => this.toast('לא ניתן לקרוא את הקובץ', 'err');
      fr.readAsText(file);
    },

    /* ---------- סרגל עליון ותפריטים ---------- */
    renderExamplesMenu() {
      const menu = $('#examples-menu');
      menu.innerHTML = M.TYPES.map((type) => {
        const list = EX.EXAMPLES.filter((e) => e.type === type);
        if (!list.length) return '';
        return '<div class="menu-group"><div class="menu-title">' + esc(M.TYPE_INFO[type].short) + '</div>' + list.map((e) =>
          '<button type="button" role="menuitem" data-example="' + e.id + '"><span class="type-tag ' + type + '">' + M.TYPE_INFO[type].en + '</span><span class="mi-text"><b>' + esc(e.name) + '</b><small>' + esc(e.description) + '</small></span></button>').join('') + '</div>';
      }).join('');
    },

    toggleMenu(id, btn, force) {
      const menu = $('#' + id);
      const open = force != null ? force : menu.hidden;
      $$('.menu').forEach((x) => { x.hidden = true; });
      $$('[aria-haspopup]').forEach((b) => b.setAttribute('aria-expanded', 'false'));
      menu.hidden = !open;
      if (btn) btn.setAttribute('aria-expanded', String(open));
      if (open) { const first = menu.querySelector('[role=menuitem]'); if (first) first.focus(); }
    },

    showModal(title, html) {
      $('#modal-title').textContent = title;
      $('#modal-body').innerHTML = html;
      $('#modal').hidden = false;
      $('#modal [data-cmd=close-modal]').focus();
    },
    closeModal() { $('#modal').hidden = true; },

    showHelp() { this.showModal('קיצורי מקלדת', D.helpHTML()); },

    bindTop() {
      const cmd = (name, el) => {
        switch (name) {
          case 'undo': this.undo(); break;
          case 'redo': this.redo(); break;
          case 'examples': this.toggleMenu('examples-menu', el); break;
          case 'save-menu': this.toggleMenu('save-menu', el); break;
          case 'save': this.toggleMenu('save-menu', null, false); this.saveFile(); break;
          case 'export-svg': this.toggleMenu('save-menu', null, false); this.exportSVG(); break;
          case 'open': $('#file-input').click(); break;
          case 'new': this.newMachine(); break;
          case 'theme': this.setTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark'); break;
          case 'help': this.showHelp(); break;
          case 'close-modal': this.closeModal(); break;
          case 'layout': this.autoLayout(); break;
          case 'fit': this.canvas.fit(); this.autosave(); break;
          case 'delete': this.deleteSelection(); break;
          case 'zoom-in': this.canvas.zoomBy(1.2); break;
          case 'zoom-out': this.canvas.zoomBy(1 / 1.2); break;
          case 'zoom-reset': this.canvas.zoomBy(1 / this.machine.view.zoom); break;
          case 'add-state': this.addStateCenter(); break;
          case 'load-example': { const ex = EX.EXAMPLES.find((e) => e.type === this.active); if (ex) this.loadExample(ex.id); break; }
          case 'run-tests': this.runPanel.runTests(); break;
        }
      };
      document.addEventListener('click', (e) => {
        const ex = e.target.closest('[data-example]');
        if (ex) { this.toggleMenu('examples-menu', null, false); this.loadExample(ex.dataset.example); return; }
        const b = e.target.closest('[data-cmd]');
        if (b && !b.disabled) { cmd(b.dataset.cmd, b); return; }
        const tool = e.target.closest('[data-tool]');
        if (tool) { this.setTool(tool.dataset.tool); return; }
        const tab = e.target.closest('#type-tabs [data-type]');
        if (tab) { this.switchType(tab.dataset.type); return; }
        if (!e.target.closest('.menu-wrap')) { $$('.menu').forEach((x) => { x.hidden = true; }); $$('[aria-haspopup]').forEach((x) => x.setAttribute('aria-expanded', 'false')); }
        if (e.target.id === 'modal') this.closeModal();
      });
      $('#canvas-status').addEventListener('click', () => this.side.show('issues'));
      $('#canvas-status').addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); this.side.show('issues'); } });
      $('#file-input').addEventListener('change', (e) => { const f = e.target.files[0]; if (f) this.openFile(f); e.target.value = ''; });
      const name = $('#machine-name');
      name.addEventListener('change', () => { const v = name.value.trim() || 'מכונה ללא שם'; this.change('שם המכונה', () => { this.machine.name = v; }, null, true); });
      name.addEventListener('keydown', (e) => { e.stopPropagation(); if (e.key === 'Enter' || e.key === 'Escape') name.blur(); });
      // מקשי חצים בין הלשוניות (role=tablist). ב-RTL: שמאלה = הבא.
      $('#type-tabs').addEventListener('keydown', (e) => {
        if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
        e.preventDefault(); e.stopPropagation();
        const i = M.TYPES.indexOf(this.active);
        const ni = (i + (e.key === 'ArrowLeft' ? 1 : -1) + M.TYPES.length) % M.TYPES.length;
        this.switchType(M.TYPES[ni]);
        $('#type-tabs [data-type=' + M.TYPES[ni] + ']').focus();
      });
      // גרירת קובץ JSON אל החלון
      document.addEventListener('dragover', (e) => { if (e.dataTransfer && Array.from(e.dataTransfer.types).includes('Files')) e.preventDefault(); });
      document.addEventListener('drop', (e) => { const f = e.dataTransfer && e.dataTransfer.files[0]; if (f) { e.preventDefault(); this.openFile(f); } });
    },

    setTheme(t) {
      document.documentElement.dataset.theme = t;
      store.set(THEME_KEY, t);
      this.renderTop();
      this.canvas.render();
    },

    bindKeys() {
      document.addEventListener('keydown', (e) => {
        if (e.target.closest && e.target.closest('input, textarea, select, [contenteditable]')) return;
        if (!$('#modal').hidden) { if (e.key === 'Escape') this.closeModal(); return; }
        if (D.isOpen() || this.popover.isOpen()) return;
        const mod = e.ctrlKey || e.metaKey;
        const code = e.code;
        const running = this.isRunning();
        if (mod && code === 'KeyZ') { e.preventDefault(); e.shiftKey ? this.redo() : this.undo(); return; }
        if (mod && code === 'KeyY') { e.preventDefault(); this.redo(); return; }
        if (mod && code === 'KeyS') { e.preventDefault(); this.saveFile(); return; }
        if (mod && code === 'KeyO') { e.preventDefault(); $('#file-input').click(); return; }
        if (mod && code === 'KeyA') { e.preventDefault(); this.select(this.machine.states.map((s) => s.id), []); return; }
        if (e.altKey && /^Digit[1-4]$/.test(code)) { e.preventDefault(); this.switchType(M.TYPES[+code.slice(5) - 1]); return; }
        if (mod || e.altKey) return;
        if (code === 'Space') { if (!this.canvas.spaceDown) { this.canvas.spaceDown = true; this.canvas.host.classList.add('space'); } e.preventDefault(); return; }
        if (e.key === 'Escape') {
          if ($$('.menu').some((x) => !x.hidden)) { $$('.menu').forEach((x) => { x.hidden = true; }); return; }
          if (running || (this.run && this.run.error)) { this.stopRun(); return; }
          if (this.canvas.source) { this.canvas.arm(null); return; }
          if (this.tool !== 'select') { this.setTool('select'); return; }
          this.select([], []);
          return;
        }
        if (running) {
          if (e.key === 'ArrowRight') { e.preventDefault(); this.runCmd('next'); return; }
          if (e.key === 'ArrowLeft') { e.preventDefault(); this.runCmd('prev'); return; }
          if (e.key === 'Home') { e.preventDefault(); this.runCmd('rewind'); return; }
          if (e.key === 'End') { e.preventDefault(); this.runCmd('end'); return; }
        }
        if (code === 'KeyP') { e.preventDefault(); this.runCmd('play'); return; }
        if (e.key === 'Delete' || e.key === 'Backspace') { e.preventDefault(); this.deleteSelection(); return; }
        const one = this.sel.states.size === 1 ? Array.from(this.sel.states)[0] : null;
        if (code === 'KeyA' && this.sel.states.size) { this.change('מצב מקבל', () => { const all = Array.from(this.sel.states).every((id) => M.getState(this.machine, id).accepting); this.sel.states.forEach((id) => M.setAccepting(this.machine, id, !all)); }); return; }
        if (code === 'KeyI' && one) { this.stateAction('start', one); return; }
        if ((e.key === 'F2' || e.key === 'Enter') && one) { e.preventDefault(); this.canvas.openRenameEditor(one); return; }
        if ((e.key === 'F2' || e.key === 'Enter') && this.sel.trans.size === 1) { e.preventDefault(); this.openTransitionEdge(Array.from(this.sel.trans)[0]); return; }
        if (code === 'KeyV') return this.setTool('select');
        if (code === 'KeyS' || code === 'KeyN') { this.addStateCenter(); return; }
        if (code === 'KeyT' && one) { this.stateAction('connect', one); return; }
        if (code === 'KeyH') return this.setTool('hand');
        if (code === 'KeyL') return this.autoLayout();
        if (code === 'KeyF') { this.canvas.fit(); this.autosave(); return; }
        if (e.key === '+' || e.key === '=') return this.canvas.zoomBy(1.2);
        if (e.key === '-' || e.key === '_') return this.canvas.zoomBy(1 / 1.2);
        if (e.key === '0') return this.canvas.zoomBy(1 / this.machine.view.zoom);
        if (e.key === '?') return this.showHelp();
        if (e.key.startsWith('Arrow') && this.sel.states.size && !running) {
          e.preventDefault();
          const d = e.shiftKey ? 20 : 5;
          const dx = e.key === 'ArrowLeft' ? -d : e.key === 'ArrowRight' ? d : 0;
          const dy = e.key === 'ArrowUp' ? -d : e.key === 'ArrowDown' ? d : 0;
          const snap = this.nudgeSnap || this.snapshot();
          this.sel.states.forEach((id) => { const s = M.getState(this.machine, id); M.moveState(this.machine, id, s.x + dx, s.y + dy); });
          this.canvas.render();
          this.nudgeSnap = snap;
          clearTimeout(this.nudgeT);
          this.nudgeT = setTimeout(() => { this.change('הזזה', null, this.nudgeSnap); this.nudgeSnap = null; }, 400);
        }
      });
      document.addEventListener('keyup', (e) => { if (e.code === 'Space') { this.canvas.spaceDown = false; this.canvas.host.classList.remove('space'); } });
      window.addEventListener('blur', () => { this.canvas.spaceDown = false; this.canvas.host.classList.remove('space'); });
    }
  });

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', () => app.init());
  else app.init();
})(typeof globalThis !== 'undefined' ? globalThis : this);
