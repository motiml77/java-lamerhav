/* lang/index.js - טוען את כל חלקי AutomataLang ב-Node (בדפדפן הסדר נקבע ב-index.html). */
'use strict';
const L = require('./ns.js');
['lex', 'parse', 'regex', 'oracle', 'fa', 'classify', 'build', 'pda', 'tm', 'extra', 'vec', 'vec-build', 'combine', 'pdax', 'check', 'templates', 'exp-edit'].forEach((f) => require('./' + f + '.js'));
module.exports = L;
