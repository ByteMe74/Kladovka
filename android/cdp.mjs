// CDP-утилита: открывает свежую вкладку кабинета и выполняет Runtime.evaluate
import { readFileSync } from 'node:fs';
const base = 'http://127.0.0.1:9222';
const URL = 'https://kladovka.dr6ter.ru/cabinet/';
const created = await (await fetch(base + '/json/new?' + encodeURIComponent(URL), { method: 'PUT' })).json();
const ws = new WebSocket(created.webSocketDebuggerUrl);
await new Promise((res, rej) => {
  const t = setTimeout(() => rej(new Error('ws open timeout')), 10000);
  ws.onopen = () => { clearTimeout(t); res(); };
  ws.onerror = () => { clearTimeout(t); rej(new Error('ws error')); };
});
let id = 0;
const pending = new Map();
ws.onmessage = (e) => {
  const m = JSON.parse(e.data);
  if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
};
function send(method, params = {}) {
  return new Promise((res, rej) => {
    const i = ++id;
    const t = setTimeout(() => { pending.delete(i); rej(new Error(method + ' timeout')); }, 15000);
    pending.set(i, (m) => { clearTimeout(t); res(m); });
    ws.send(JSON.stringify({ id: i, method, params }));
  });
}
await send('Runtime.enable');
for (let i = 0; i < 40; i++) {
  try {
    const r = await send('Runtime.evaluate', { expression: 'document.readyState', returnByValue: true });
    if (r.result?.result?.value === 'complete') break;
  } catch { /* ещё раз */ }
  await new Promise(r => setTimeout(r, 500));
}
let expr = process.argv[2];
if (expr && expr.startsWith('@')) expr = readFileSync(expr.slice(1), 'utf8');
const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true });
if (r.result?.exceptionDetails) {
  console.log('EXC: ' + (r.result.exceptionDetails.exception?.description || r.result.exceptionDetails.text));
} else {
  console.log(JSON.stringify(r.result?.result?.value ?? null));
}
ws.close();
process.exit(0);