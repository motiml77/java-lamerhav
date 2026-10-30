// חיקוי של ה-QuickPick / InputBox של VS Code (Light Modern) — לסרטונים: "דוגמאות פתורות" ו"תרגיל חדש" ב-VS Code הם רשימות בחירה של VS Code עצמו.
//   html({ title, placeholder, value, items: [{ label, description, picked }], active, many, prompt, message }) → דף; הווידג'ט בפינה (0,0)
//   size(o) → [רוחב, גובה] של הווידג'ט
const esc = s => String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const W = 622;
function size(o) {
  const items = (o.items || []).length;
  return [W, 30 + 34 + items * 24 + (o.prompt ? 26 : 0) + (o.message ? 30 : 0) + 8];
}
function html(o) {
  const rows = (o.items || []).map((it, i) => `<button class="row${i === o.active ? ' act' : ''}">${o.many ? `<span class="cb${it.picked ? ' on' : ''}">${it.picked ? '✓' : ''}</span>` : ''}<span class="lbl">${esc(it.label)}</span>${it.description ? `<span class="desc">${esc(it.description)}</span>` : ''}</button>`).join('');
  return `<!DOCTYPE html><html><head><meta charset="UTF-8"><style>
body { margin: 0; background: #f8f8f8; font: 13px/1.4 var(--vscode-font-family); overflow: hidden; }
.qp { width: ${W - 2}px; background: #f8f8f8; border: 1px solid #e5e5e5; padding: 0 0 6px; }
.t { height: 30px; display: flex; align-items: center; justify-content: center; font-size: 13px; color: #3b3b3b; position: relative; padding: 0 70px; white-space: nowrap; overflow: hidden; }
.t .ok { position: absolute; right: 8px; top: 4px; height: 22px; padding: 0 12px; border-radius: 3px; background: #005fb8; color: #fff; font-size: 12px; line-height: 22px; }
.in { margin: 2px 6px 6px; height: 26px; border: 1px solid #005fb8; border-radius: 4px; background: #fff; display: flex; align-items: center; padding: 0 6px; color: #3b3b3b; white-space: nowrap; overflow: hidden; unicode-bidi: plaintext; }
.in .ph { color: #8b8b8b; }
.pr { height: 26px; display: flex; align-items: center; padding: 0 10px; color: #3b3b3b; font-size: 12.5px; white-space: nowrap; overflow: hidden; }
.msg { margin: 0 6px 4px; padding: 5px 8px; border: 1px solid #1a85ff; background: #d6ecff; color: #1b3d63; font-size: 12.5px; white-space: nowrap; overflow: hidden; unicode-bidi: plaintext; }
.row { display: flex; width: 100%; align-items: center; gap: 8px; height: 24px; padding: 0 12px; border: 0; background: none; font: inherit; color: #3b3b3b; text-align: left; white-space: nowrap; }
.row .desc { color: #717171; font-size: 12.5px; overflow: hidden; text-overflow: ellipsis; }
.row.act { background: #e8e8e8; outline: 1px solid #005fb8; outline-offset: -1px; }
.row .lbl { unicode-bidi: plaintext; }
.cb { width: 14px; height: 14px; border: 1px solid #919191; border-radius: 3px; display: inline-flex; align-items: center; justify-content: center; font-size: 11px; color: #fff; flex: none; }
.cb.on { background: #005fb8; border-color: #005fb8; }
</style></head><body><div class="qp"><div class="t">${esc(o.title)}${o.many ? '<span class="ok">OK</span>' : ''}</div>
<div class="in">${o.value != null && o.value !== '' ? esc(o.value) : `<span class="ph">${esc(o.placeholder || '')}</span>`}</div>
${o.prompt ? `<div class="pr">${esc(o.prompt)}</div>` : ''}${o.message ? `<div class="msg">${esc(o.message)}</div>` : ''}${rows}</div></body></html>`;
}
module.exports = { html, size };
