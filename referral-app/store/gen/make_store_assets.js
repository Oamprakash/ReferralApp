// Builds the Play Store graphics:
//   ../play/screenshot_1..5.png   1080x1920 phone screenshots (caption + framed app screen)
//   ../play/feature_graphic.png   1024x500
// Real screenshots: put them in ../screens/ as 1.png .. 5.png (any phone size, portrait) and re-run.
// Missing ones fall back to the drawn mock screens in mock_screens.html.
// Run:  node make_store_assets.js   (needs Playwright)
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const here = __dirname;
const out = path.join(here, '..', 'play');
const real = path.join(here, '..', 'screens');
fs.mkdirSync(out, { recursive: true });

const shots = [
  { id: 'home',    title: 'Jobs posted by <em>people who work there</em>', sub: 'Every opening comes from a verified employee' },
  { id: 'foryou',  title: 'See how well <em>you match</em>',               sub: 'Roles ranked by your skills and experience' },
  { id: 'detail',  title: 'Ask for a <em>referral</em> in one tap',         sub: 'See who posted it and how many spots are left' },
  { id: 'applied', title: 'Track <em>every step</em>',                      sub: 'Shortlisted, referred, interview, offer' },
  { id: 'posts',   title: 'Hiring at your company? <em>Refer talent</em>',  sub: 'Verify your work email and share real openings' },
];

const fontCss = ['regular:400', 'medium:500', 'semibold:600', 'bold:700', 'extrabold:800'].map(s => {
  const [n, w] = s.split(':');
  return `@font-face{font-family:J;src:url(file://${here}/plus_jakarta_sans_${n}.ttf);font-weight:${w}}`;
}).join('');

const frameHtml = (img, s) => `<!doctype html><html><head><meta charset="utf-8"><style>${fontCss}
*{margin:0;box-sizing:border-box}
body{width:1080px;height:1920px;overflow:hidden;font-family:J;background:linear-gradient(160deg,#0F766E 0%,#134E7A 55%,#1E3A8A 100%);position:relative}
.glow{position:absolute;width:900px;height:900px;border-radius:50%;background:radial-gradient(circle,rgba(252,211,77,.22),transparent 65%);top:-380px;right:-360px}
.cap{position:absolute;top:120px;left:90px;right:90px;color:#fff}
h1{font-size:78px;line-height:88px;font-weight:800;letter-spacing:-.025em}
h1 em{font-style:normal;color:#FCD34D}
p{margin-top:26px;font-size:38px;line-height:50px;color:rgba(255,255,255,.82);font-weight:500}
.phone{position:absolute;left:50%;transform:translateX(-50%);top:560px;width:760px;border-radius:78px;background:#0B1220;padding:16px;box-shadow:0 40px 90px rgba(5,10,30,.45)}
.phone img{display:block;width:100%;border-radius:64px}
</style></head><body><div class="glow"></div>
<div class="cap"><h1>${s.title}</h1><p>${s.sub}</p></div>
<div class="phone"><img src="file://${img}"></div></body></html>`;

const featureHtml = (img) => `<!doctype html><html><head><meta charset="utf-8"><style>${fontCss}
*{margin:0;box-sizing:border-box}
body{width:1024px;height:500px;overflow:hidden;font-family:J;background:linear-gradient(135deg,#0F766E,#1E3A8A);position:relative;color:#fff}
.glow{position:absolute;width:700px;height:700px;border-radius:50%;background:radial-gradient(circle,rgba(252,211,77,.2),transparent 65%);top:-300px;left:-200px}
.l{position:absolute;left:64px;top:70px;width:560px}
.icon{width:96px;height:96px;border-radius:26px;background:rgba(255,255,255,.12)}
h1{font-size:50px;line-height:56px;font-weight:800;letter-spacing:-.02em;margin-top:26px}
h1 em{font-style:normal;color:#FCD34D}
p{font-size:22px;color:rgba(255,255,255,.82);margin-top:16px;font-weight:500}
.phone{position:absolute;right:70px;top:46px;width:300px;border-radius:40px;background:#0B1220;padding:9px;transform:rotate(6deg);box-shadow:0 30px 60px rgba(5,10,30,.45)}
.phone img{display:block;width:100%;border-radius:32px}
</style></head><body><div class="glow"></div>
<div class="l"><img class="icon" src="file://${here}/icon.svg"><h1>Get referred by people <em>who work there</em></h1>
<p>Verified employees. Real openings. Free for job seekers.</p></div>
<div class="phone"><img src="file://${img}"></div></body></html>`;

// Local fonts and images only load when the page itself is a file:// URL.
async function openHtml(page, html) {
  const tmp = path.join(here, '_render.html');
  fs.writeFileSync(tmp, html);
  await page.goto('file://' + tmp, { waitUntil: 'load' });
  await page.evaluate(() => document.fonts.ready);
}

(async () => {
  const browser = await chromium.launch();

  // 1. App screens: real screenshot if provided, else render the mock at 3x (1080 px wide)
  const screenFiles = [];
  const mock = await browser.newPage({ viewport: { width: 360, height: 760 }, deviceScaleFactor: 3 });
  await mock.goto('file://' + path.join(here, 'mock_screens.html'));
  await mock.waitForTimeout(400);
  for (let i = 0; i < shots.length; i++) {
    const realFile = path.join(real, `${i + 1}.png`);
    if (fs.existsSync(realFile)) { screenFiles.push(realFile); continue; }
    const f = path.join(here, `mock_${shots[i].id}.png`);
    await (await mock.$('#' + shots[i].id)).screenshot({ path: f });
    screenFiles.push(f);
  }

  // 2. Framed screenshots
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 } });
  for (let i = 0; i < shots.length; i++) {
    await openHtml(page, frameHtml(screenFiles[i], shots[i]));
    await page.waitForTimeout(300);
    await page.screenshot({ path: path.join(out, `screenshot_${i + 1}.png`) });
  }

  // 3. Feature graphic (no transparency; Play requires an opaque 1024x500)
  const fg = await browser.newPage({ viewport: { width: 1024, height: 500 } });
  await openHtml(fg, featureHtml(screenFiles[0]));
  await fg.waitForTimeout(300);
  await fg.screenshot({ path: path.join(out, 'feature_graphic.png'), omitBackground: false });

  fs.rmSync(path.join(here, '_render.html'), { force: true });
  await browser.close();
  console.log('Wrote', fs.readdirSync(out).join(', '));
})();
