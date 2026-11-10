/*
 * icons.js - אייקונים מקוריים, SVG בתוך הקוד. רשת 20×20, קו 1.6, קצוות מעוגלים, צבע currentColor.
 * שימוש: Icons.get('play') מחזיר מחרוזת <svg>.
 */
(function (root) {
  'use strict';
  const P = {
    select: '<path d="M5 3.5l9.5 5.6-4.2 1.1 2.6 4.6-1.9 1.1-2.6-4.6-3 3.1z" fill="currentColor" stroke="none"/>',
    state: '<circle cx="9" cy="10" r="5.6"/><path d="M15.5 3.5v4M13.5 5.5h4"/>',
    transition: '<circle cx="4.5" cy="13.5" r="2.4"/><circle cx="15.5" cy="6.5" r="2.4"/><path d="M6.6 12.2c2.2-3.6 4.4-5 6.4-5.3"/><path d="M11 5.6l2 1.3-1.2 2"/>',
    hand: '<path d="M7 10V4.8a1.3 1.3 0 012.6 0V9.5M9.6 9V3.6a1.3 1.3 0 012.6 0V9M12.2 9V4.6a1.3 1.3 0 012.6 0v6.6c0 3.2-2.1 5.6-5.2 5.6-2.1 0-3.4-1-4.6-2.7L3.3 11a1.3 1.3 0 012-1.5L7 11.2"/>',
    layout: '<circle cx="4.5" cy="10" r="2.2"/><circle cx="15.5" cy="4.8" r="2.2"/><circle cx="15.5" cy="15.2" r="2.2"/><path d="M6.6 9.1l6.8-3.3M6.6 10.9l6.8 3.3"/>',
    fit: '<path d="M3.5 7.5v-4h4M16.5 7.5v-4h-4M3.5 12.5v4h4M16.5 12.5v4h-4"/><rect x="7" y="7" width="6" height="6" rx="1.2"/>',
    trash: '<path d="M4 5.5h12M8 5.5V4h4v1.5M5.6 5.5l.7 10.2c.1.8.7 1.3 1.4 1.3h4.6c.7 0 1.3-.5 1.4-1.3l.7-10.2M8.4 8.5v5.5M11.6 8.5v5.5"/>',
    undo: '<path d="M7.5 5L4 8.5 7.5 12"/><path d="M4.5 8.5h7.2a4 4 0 010 8H9"/>',
    redo: '<path d="M12.5 5L16 8.5 12.5 12"/><path d="M15.5 8.5H8.3a4 4 0 000 8H11"/>',
    open: '<path d="M3 6.2c0-.9.7-1.7 1.7-1.7h3.1l1.7 1.8h5.8c.9 0 1.7.7 1.7 1.7v6.3c0 .9-.8 1.7-1.7 1.7H4.7c-1 0-1.7-.8-1.7-1.7z"/>',
    save: '<path d="M10 3.5v9M6.2 8.8L10 12.6l3.8-3.8M4 13.5v1.8c0 .7.6 1.2 1.2 1.2h9.6c.6 0 1.2-.5 1.2-1.2v-1.8"/>',
    book: '<path d="M10 5.5c-1.6-1.3-3.6-1.8-6.3-1.6v11c2.7-.2 4.7.3 6.3 1.6 1.6-1.3 3.6-1.8 6.3-1.6v-11c-2.7-.2-4.7.3-6.3 1.6zM10 5.5v11"/>',
    sun: '<circle cx="10" cy="10" r="3.3"/><path d="M10 2.8v1.6M10 15.6v1.6M2.8 10h1.6M15.6 10h1.6M4.9 4.9l1.1 1.1M14 14l1.1 1.1M4.9 15.1L6 14M14 6l1.1-1.1"/>',
    moon: '<path d="M15.8 12.3A6.5 6.5 0 017.7 4.2a6.5 6.5 0 108.1 8.1z"/>',
    help: '<circle cx="10" cy="10" r="7"/><path d="M8 8a2 2 0 113 1.7c-.7.4-1 .9-1 1.6v.4"/><circle cx="10" cy="14.2" r=".4" fill="currentColor"/>',
    keyboard: '<rect x="2.5" y="5" width="15" height="10" rx="1.8"/><path d="M5.5 8h.01M8.5 8h.01M11.5 8h.01M14.5 8h.01M5.5 11h.01M14.5 11h.01M8 11.5h4"/>',
    play: '<path d="M6.5 4.6v10.8c0 .5.5.8.9.5l8.1-5.4a.6.6 0 000-1L7.4 4.1c-.4-.3-.9 0-.9.5z" fill="currentColor" stroke="none"/>',
    pause: '<rect x="5.5" y="4.5" width="3" height="11" rx="1" fill="currentColor" stroke="none"/><rect x="11.5" y="4.5" width="3" height="11" rx="1" fill="currentColor" stroke="none"/>',
    next: '<path d="M5 5.2v9.6c0 .4.5.7.8.4l6.6-4.8a.5.5 0 000-.8L5.8 4.8c-.3-.3-.8 0-.8.4z" fill="currentColor" stroke="none"/><path d="M15 4.8v10.4"/>',
    prev: '<path d="M15 5.2v9.6c0 .4-.5.7-.8.4l-6.6-4.8a.5.5 0 010-.8l6.6-4.8c.3-.3.8 0 .8.4z" fill="currentColor" stroke="none"/><path d="M5 4.8v10.4"/>',
    rewind: '<path d="M4.5 4.5v11M15.5 5.6v8.8L9 10z"/>',
    end: '<path d="M15.5 4.5v11M4.5 5.6v8.8L11 10z"/>',
    check: '<path d="M4.5 10.5l3.5 3.5 7.5-8"/>',
    x: '<path d="M5.5 5.5l9 9M14.5 5.5l-9 9"/>',
    warn: '<path d="M10 3.5l7 12.5H3z"/><path d="M10 8.5v3.2"/><circle cx="10" cy="13.9" r=".4" fill="currentColor"/>',
    info: '<circle cx="10" cy="10" r="7"/><path d="M10 9v4.5"/><circle cx="10" cy="6.6" r=".4" fill="currentColor"/>',
    zoomIn: '<path d="M10 4.5v11M4.5 10h11"/>',
    zoomOut: '<path d="M4.5 10h11"/>',
    start: '<path d="M2.5 10h6M6.5 7.5L9 10l-2.5 2.5"/><circle cx="13.5" cy="10" r="4"/>',
    accept: '<circle cx="10" cy="10" r="6.5"/><circle cx="10" cy="10" r="3.8"/>',
    loop: '<path d="M7.4 12.2a4.2 4.2 0 115.2 0"/><path d="M12.8 9.6l-.2 2.6 2.5.4"/><circle cx="10" cy="16" r="1.6"/>',
    edit: '<path d="M4 16l.8-3.4 8.4-8.4a1.6 1.6 0 012.3 2.3l-8.4 8.4z"/><path d="M11.8 5.6l2.6 2.6"/>',
    plus: '<path d="M10 4.5v11M4.5 10h11"/>',
    chevronDown: '<path d="M6 8l4 4 4-4"/>',
    sigma: '<path d="M14.5 4.5h-9l5 5.5-5 5.5h9"/>',
    list: '<path d="M7.5 6h9M7.5 10h9M7.5 14h9"/><circle cx="4" cy="6" r=".6" fill="currentColor"/><circle cx="4" cy="10" r=".6" fill="currentColor"/><circle cx="4" cy="14" r=".6" fill="currentColor"/>',
    sliders: '<path d="M4 6h7M15 6h1M4 14h1M9 14h7"/><circle cx="13" cy="6" r="2"/><circle cx="7" cy="14" r="2"/>',
    beaker: '<path d="M8 3.5h4M8.6 3.5v4.6L4.4 15c-.5.8.1 1.5 1 1.5h9.2c.9 0 1.5-.7 1-1.5l-4.2-6.9V3.5"/><path d="M6.2 12.5h7.6"/>',
    tape: '<rect x="2.5" y="6.5" width="15" height="7" rx="1"/><path d="M6.5 6.5v7M10 6.5v7M13.5 6.5v7"/>',
    stack: '<rect x="5" y="3.5" width="10" height="3.6" rx="1"/><rect x="5" y="8.2" width="10" height="3.6" rx="1"/><rect x="5" y="12.9" width="10" height="3.6" rx="1"/>',
    bank: '<path d="M6 3.5h9.2c.7 0 1.3.6 1.3 1.3V14"/><rect x="3.5" y="6" width="10.5" height="10.5" rx="1.5"/><rect x="5.6" y="8.2" width="1.8" height="1.8" rx=".4" fill="currentColor" stroke="none"/><rect x="5.6" y="12.3" width="1.8" height="1.8" rx=".4" fill="currentColor" stroke="none"/><path d="M9.2 9.1h3M9.2 13.2h3"/>',
  };
  function get(name, size) {
    const s = size || 20;
    const body = P[name] || P.info;
    return '<svg class="icon" width="' + s + '" height="' + s + '" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">' + body + '</svg>';
  }
  root.Icons = { get, names: Object.keys(P) };
})(typeof globalThis !== 'undefined' ? globalThis : this);
