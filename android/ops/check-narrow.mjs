// Проверка адаптивности сайта на узких экранах через CDP.
//
// Зачем она нужна. Штатный `--window-size=360` у headless Edge врёт: страница
// рендерится при innerWidth = 504 px, а снимок обрезается до 360. Выглядит
// это как «текст обрезан справа», и чтобы «починить», в CSS добавляют
// переносы там, где переносы не нужны. Замер доказал обратное: реальная ширина
// 504 при картинке в 360 (проверено страницей, печатающей innerWidth прямо в
// снимок). Поэтому ширину здесь задаёт Emulation.setDeviceMetricsOverride —
// она меняет именноCSS-ширину, а не окно.
//
// Что делает:
//   1. Открывает страницу на каждой ширине из аргумента.
//   2. Меряет documentElement.scrollWidth против clientWidth и ищет элементы,
//      вылезающие за правый край (переполнение по горизонтали).
//   3. Кладёт снимок (по возможности на всю высоту) в указанный каталог.
//
// Запуск (нужен браузер с отладочным портом 9222):
//   node ops/check-narrow.mjs https://kladovka.dr6ter.ru/apk-permissions.php 320 360 430
//
// Браузер поднимается так:
//   msedge.exe --headless=new --remote-debugging-port=9222 --user-data-dir=<каталог>

const base = 'http://127.0.0.1:9222';
const page = process.argv[2];
const widths = process.argv.slice(3).filter((a) => /^\d+$/.test(a)).map(Number);
const outDir = process.env.SHOT_DIR || 'shots';
const wantShots = process.env.NO_SHOTS !== '1';
// Полный снимок страницы высотой 15 000 px нечитаем: в превью он сжимается до
// неразличимой полоски. Для разглядывания конкретного блока удобнее снимок
// только видимой области после прокрутки к нужному месту.
const shotViewport = process.env.SHOT_VIEWPORT === '1';
const scrollTo = process.env.SHOT_SCROLL || '';

if (!page || widths.length === 0) {
  console.error('Использование: node check-narrow.mjs <url> <ширина> [ширина…]');
  process.exit(2);
}

async function open(url) {
  const created = await (await fetch(base + '/json/new?' + encodeURIComponent(url), { method: 'PUT' })).json();
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
  const send = (method, params = {}) => new Promise((res, rej) => {
    const i = ++id;
    const t = setTimeout(() => { pending.delete(i); rej(new Error(method + ' timeout')); }, 30000);
    pending.set(i, (m) => { clearTimeout(t); res(m); });
    ws.send(JSON.stringify({ id: i, method, params }));
  });
  return { ws, send, targetId: created.id };
}

// Скрипт в странице: ищем переполнение по горизонтали.
const MEASURE = `(() => {
  const dw = document.documentElement.clientWidth;
  const sw = document.documentElement.scrollWidth;
  const bad = [];
  for (const el of document.querySelectorAll('body *')) {
    const r = el.getBoundingClientRect();
    if (r.width === 0 || r.height === 0) continue;
    if (r.right > dw + 1) {
      const cs = getComputedStyle(el);
      // Элемент может лежать в контейнере, который его обрезает или
      // прокручивает по горизонтали: overflow hidden/auto/scroll — это
      // намеренное поведение (карусель скриншотов на главной), а не дефект
      // вёрстки. Считать его бакетом нельзя, иначе инструмент будет требовать
      // «починки» того, что сделано нарочно.
      let clipped = false;
      for (let p = el.parentElement; p; p = p.parentElement) {
        const pcs = getComputedStyle(p);
        if (/hidden|auto|scroll|clip/.test(pcs.overflowX) || pcs.overflow === 'hidden') { clipped = true; break; }
        if (p === document.body) break;
      }
      if (clipped) continue;
      bad.push({
        tag: el.tagName,
        cls: (el.className || '').toString().slice(0, 28),
        right: Math.round(r.right),
        width: Math.round(r.width),
        text: (el.textContent || '').trim().slice(0, 40),
        ws: cs.whiteSpace,
        ovw: cs.overflowWrap,
        wb: cs.wordBreak
      });
    }
  }
  // Изображения и видео — отдельная частая беда: шире контейнера.
  for (const img of document.querySelectorAll('img, svg, video, iframe')) {
    const r = img.getBoundingClientRect();
    if (r.right <= dw + 1) continue;
    // Та же проверка контейнера, что и выше: карусель скриншотов на главной
    // сделана как горизонтальная прокрутка, и картинки в ней по правую
    // сторону от видимой — так и задумано.
    let clipped = false;
    for (let p = img.parentElement; p; p = p.parentElement) {
      const pcs = getComputedStyle(p);
      if (/hidden|auto|scroll|clip/.test(pcs.overflowX) || pcs.overflow === 'hidden') { clipped = true; break; }
      if (p === document.body) break;
    }
    if (clipped) continue;
    bad.push({ tag: 'MEDIA ' + img.tagName, cls: (img.getAttribute('src') || '').slice(-24), right: Math.round(r.right), width: Math.round(r.width) });
  }
  return { clientWidth: dw, scrollWidth: sw, overflow: sw - dw, bad: bad.slice(0, 8), badCount: bad.length };
})()`;

const results = [];
for (const w of widths) {
  const { ws, send, targetId } = await open(page);
  try {
    await send('Page.enable');
    await send('Runtime.enable');
    await send('Emulation.setDeviceMetricsOverride', {
      width: w, height: 900, deviceScaleFactor: 1, mobile: w < 700,
    });
    // Уже открыт с правильным URL, но размеры применились после загрузки —
    // перезагружаем, чтобы переносы пересчитались под нужную ширину.
    await send('Page.reload', { ignoreCache: true });
    let ready = false;
    for (let i = 0; i < 60; i++) {
      const r = await send('Runtime.evaluate', { expression: 'document.readyState', returnByValue: true });
      if (r.result?.result?.value === 'complete') { ready = true; break; }
      await new Promise((res) => setTimeout(res, 400));
    }
    if (!ready) console.log(`  ${w}px: страница не догрузилась`);
    await new Promise((res) => setTimeout(res, 1200)); // шрифты и reveal-анимации
    const m = await send('Runtime.evaluate', { expression: MEASURE, returnByValue: true });
    const v = m.result?.result?.value || {};
    results.push({ width: w, ...v });

    if (wantShots) {
      if (scrollTo) {
        await send('Runtime.evaluate', {
          expression: `(() => { const el = document.querySelector(${JSON.stringify(scrollTo)});
            if (el) el.scrollIntoView({ block: 'start' }); return el ? el.tagName + '#' + el.id : 'нет элемента'; })()`,
          returnByValue: true,
        });
        await new Promise((res) => setTimeout(res, 900));
      }
      // Смещение после якоря: блок может быть длиннее окна, а смотреть надо
      // на его середину или низ, а не на верх под липкой шапкой.
      const scrollPx = Number(process.env.SHOT_SCROLL_PX || 0);
      if (scrollPx) {
        await send('Runtime.evaluate', { expression: `window.scrollBy(0, ${scrollPx})`, returnByValue: true });
        await new Promise((res) => setTimeout(res, 600));
      }
      let shotHeight = 900;
      if (!shotViewport) {
        const hRes = await send('Runtime.evaluate', {
          expression: 'Math.min(document.documentElement.scrollHeight, 6000)', returnByValue: true,
        });
        shotHeight = hRes.result?.result?.value || 900;
        await send('Emulation.setDeviceMetricsOverride', {
          width: w, height: shotHeight, deviceScaleFactor: 1, mobile: w < 700,
        });
        await new Promise((res) => setTimeout(res, 700));
      }
      const shot = await send('Page.captureScreenshot', {
        format: 'png',
        captureBeyondViewport: !shotViewport,
      });
      if (shot.result?.data) {
        const { writeFileSync, mkdirSync } = await import('node:fs');
        mkdirSync(outDir, { recursive: true });
        const name = `${outDir}/${new URL(page).pathname.replace(/\W+/g, '_') || 'page'}${scrollTo ? '_' + scrollTo.replace(/\W+/g, '') : ''}_${w}.png`;
        writeFileSync(name, Buffer.from(shot.result.data, 'base64'));
        results[results.length - 1].shot = name;
      }
    }
  } catch (e) {
    results.push({ width: w, error: String(e.message || e) });
  } finally {
    ws.close();
    await fetch(base + '/json/close/' + targetId).catch(() => {});
  }
}

let bad = 0;
for (const r of results) {
  if (r.error) { console.log(`  ${r.width}px: ОШИБКА ${r.error}`); bad++; continue; }
  const mark = r.overflow > 0 ? 'ПЕРЕПОЛНЕНИЕ' : 'ок';
  console.log(`  ${String(r.width).padStart(4)}px  ${mark}  client=${r.clientWidth} scroll=${r.scrollWidth} (${r.overflow > 0 ? '+' : ''}${r.overflow})  вылезает: ${r.badCount}${r.shot ? '  ' + r.shot : ''}`);
  for (const b of r.bad || []) {
    bad++;
    console.log(`         ${b.tag}.${b.cls} right=${b.right} w=${b.width} ws=${b.ws} ovw=${b.ovw} wb=${b.wb} «${(b.text || '').replace(/\s+/g, ' ')}»`);
  }
  if (r.overflow > 0) bad++;
}
process.exit(bad > 0 ? 1 : 0);
