const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const { pathToFileURL } = require('node:url');

(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1030 }, deviceScaleFactor: 2 });
  const output = path.resolve(__dirname, '../../docs/visual-refresh');
  const errors = [];
  const checks = [];
  page.on('pageerror', error => errors.push(error.message));
  const check = (name, condition) => { assert.ok(condition, name); checks.push(name); };
  const capture = name => page.screenshot({ path: path.join(output, `${name}.png`), fullPage: true });
  const capturePhone = name => page.locator('#phone').screenshot({ path: path.join(output, `${name}.png`) });
  const selectScene = value => page.locator(`.scenes [data-scene="${value}"]`).click();

  try {
    await page.goto(pathToFileURL(path.join(__dirname, 'index.html')).href);
    check('initial four active todo rows', await page.locator('#rows .item').count() === 4);
    check('no legacy bottom navigation bar', await page.locator('.app-nav').count() === 0);
    check('floating dock CTA exists', await page.locator('.floating-dock .fab-pill').count() === 1);
    check('three list bookmark chips', await page.locator('.bookmark-chip').count() === 3);
    check('two widget instances', await page.locator('#widgets .widget').count() === 2);

    await capture('overview');
    await capturePhone('phone-todo-light');

    // Complete item & undo
    await page.locator('#rows .check').first().click();
    check('completion removes active row', await page.locator('#rows .item').count() === 3);
    check('completion updates widget count', (await page.locator('#widgets .widget').first().locator('.count').textContent()).includes('3'));
    await page.locator('#undo').click();
    check('undo restores row', await page.locator('#rows .item').count() === 4);

    // Search & filter
    await page.locator('#search').fill('照片');
    check('search filters list', await page.locator('#rows .item').count() === 1);
    await page.locator('#search').fill('不存在');
    check('search empty state', (await page.locator('#rows').textContent()).includes('没有找到'));
    await page.locator('[data-clear-search]').click();
    await page.locator('[data-filter="done"]').click();
    check('completed filter', await page.locator('#rows .done').count() === 1);
    await page.locator('[data-filter="active"]').click();

    // Switch list via bookmark chip
    await page.locator('[data-select-list="weekend"]').click();
    check('bookmark chip switches list', await page.locator('#rows .item').count() === 2);
    await page.locator('[data-select-list="todo"]').click();

    // Open Notepad Quick Entry
    await page.locator('.floating-dock [data-add]').click();
    await page.locator('[name="title"]').fill('买一束秋日尤加利叶');
    await page.locator('[name="note"]').fill('放在餐桌玻璃瓶里');
    await page.locator('#pill-pin').click();
    await capture('editor');
    await capturePhone('phone-editor-light');

    const dialogBounds = await page.locator('dialog').boundingBox();
    const phoneBounds = await page.locator('.phone').boundingBox();
    check('editor fits phone width', dialogBounds.x >= phoneBounds.x && dialogBounds.x + dialogBounds.width <= phoneBounds.x + phoneBounds.width + 2);

    await page.locator('#entry-form [type="submit"]').click();
    check('save closes editor', !(await page.locator('dialog').isVisible()));
    check('save adds record', await page.locator('#rows .item').count() === 5);

    // Manage Lists screen
    await selectScene('manage');
    check('manage lists shows 3 lists', await page.locator('.manage-row').count() === 3);
    await capturePhone('phone-manage-light');

    // Widget Config screen
    await selectScene('config');
    await page.locator('[data-cfg-key="size"][data-cfg-val="small"]').click();
    await page.locator('[data-cfg-key="density"][data-cfg-val="compact"]').click();
    check('config preview updates to compact mode', await page.locator('#config-preview .widget.compact-mode').count() === 1);
    await page.locator('[data-cfg-key="theme"][data-cfg-val="dark"]').click();
    await capture('config');
    await capturePhone('phone-config-light');

    // Settings screen
    await selectScene('settings');
    await capturePhone('phone-settings-light');
    await page.locator('[data-app-theme="dark"]').click();
    check('theme changes to dark', await page.locator('body').evaluate(element => element.classList.contains('dark')));

    // Night mode overview
    await selectScene('todo');
    await page.locator('#wall').click();
    await page.locator('#material').click();
    check('native mode disables backdrop blur', await page.locator('#widgets .widget').first().evaluate(element => getComputedStyle(element).backdropFilter === 'none'));
    await capture('night');
    await capturePhone('phone-todo-dark');

    // Restore light & reset
    await page.locator('#theme').click();
    await page.locator('#wall').click();
    await page.locator('#material').click();
    await page.locator('#reset').click();

    await page.setViewportSize({ width: 390, height: 844 });
    await capture('mobile');

    for (const width of [320, 390, 768, 1024, 1440]) {
      await page.setViewportSize({ width, height: 1030 });
      check(`no page overflow at ${width}px`, await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
      check(`no list horizontal overflow at ${width}px`, await page.locator('.list-area').evaluate(element => element.scrollWidth <= element.clientWidth));
    }

    check('no runtime errors', errors.length === 0);
    fs.writeFileSync(path.join(output, 'verification.json'), JSON.stringify({
      date: '2026-11-15',
      version: 'v0.4.0-visual-study-03',
      direction: 'Direction A: Warm Notebook & Paper Evolution',
      engine: 'Playwright / headless Edge',
      checks,
      errors
    }, null, 2));
    process.stdout.write(`Passed ${checks.length} checks\n`);
  } finally {
    await browser.close();
  }
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
