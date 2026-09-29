/* The window at the lab PCs' screens -- 1920x1080 at 100, 125 and 150 %,
   maximised over a 48 px taskbar -- and at half a screen: every panel
   there, no title or button cut, nothing past the window's edge; the
   toolbar in the bar or in its own row, whole; panels that are empty say
   what fills them (one character: the Canvas's); the splitters and the
   bottom panel's fold.  (The » rule, the tight window and the folds for the
   room: shell.e2e.ts, D-158.) */

import { expect, test, type Page } from '@playwright/test';

import { DATAPATH, launch, openFile, sample } from './harness.ts';

// Everything with words that must be read whole, and that is on screen.
async function clipped(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const out: string[] = [];
    const sel = '.ptitle, .ptab, .btn .label, .seg .label, .appname, .titlebar .file b, .notice h3, .notice p, .status > span:not(.grow), .action b, .action .sub, .list li, .libgroup summary, .hbtn, select';
    for (const el of document.querySelectorAll<HTMLElement>(sel)) {
      if (!el.checkVisibility({ visibilityProperty: true })) continue;
      const r = el.getBoundingClientRect();
      if (r.width === 0) continue;
      const name = `${el.className || el.tagName}: ${el.textContent?.trim().slice(0, 40)}`;
      if (el.scrollWidth > el.clientWidth + 1 && getComputedStyle(el).overflowX !== 'auto') out.push(`cut: ${name}`);
      // Past the window's edge -- unless it is in a list that scrolls (reached by scrolling it).
      let scrolled = false;
      for (let p = el.parentElement; p && !scrolled; p = p.parentElement) scrolled = /auto|scroll/.test(getComputedStyle(p).overflowY + getComputedStyle(p).overflowX);
      if (!scrolled && (r.right > window.innerWidth + 0.5 || r.bottom > window.innerHeight + 0.5 || r.left < -0.5)) out.push(`outside: ${name}`);
    }
    return out;
  });
}

// English names inside Korean sentences broken across two lines ("Data" / "Bits").
async function brokenNames(page: Page): Promise<string[]> {
  return page.evaluate(() => [...document.querySelectorAll<HTMLElement>('.name')]
    .filter((e) => e.checkVisibility({ visibilityProperty: true }) && e.getClientRects().length > 1).map((e) => e.textContent ?? ''));
}

const SCREENS = [
  { scale: 1, size: { width: 1920, height: 1032 } },
  { scale: 1.25, size: { width: 1536, height: 816 } },
  { scale: 1.5, size: { width: 1280, height: 672 } },
];

for (const { scale, size } of SCREENS) {
  test(`1920x1080 at ${scale * 100} %: every panel there, nothing cut, nothing outside`, async () => {
    const r = await launch(size, { switches: [`--force-device-scale-factor=${scale}`] });
    const { page } = r;
    try {
      expect(await page.evaluate(() => window.devicePixelRatio)).toBe(scale);
      expect(await clipped(page)).toEqual([]);   // the first screen
      await openFile(r, sample(r.dir, DATAPATH));
      await page.evaluate(() => document.fonts.ready);
      for (const name of ['Components', 'Tunnels', 'Canvas', 'Messages', 'Attributes']) {
        const panel = page.locator(`section[aria-label="${name}"]`);
        await expect(panel, name).toBeVisible();
        const box = (await panel.boundingBox())!;
        expect(box.width, name).toBeGreaterThan(150);
        expect(box.height, name).toBeGreaterThan(90);
      }
      await expect(page.locator('.shell')).not.toHaveClass(/narrow/);
      expect(await clipped(page)).toEqual([]);
      // Nothing selected: the circuit's attributes (N-10, Y-05), every name whole
      await expect(page.locator('section.right .aname')).toHaveText('main');
      await expect(page.locator('section.right .atable tbody th')).toHaveCount(4);
      expect(await brokenNames(page)).toEqual([]);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
      // The title bar: its right end clear of the caption buttons, every toolbar button whole and inside the window.
      expect(await page.evaluate(() => {
        const bar = document.querySelector('.titlebar')!;
        const end = bar.getBoundingClientRect().right - parseFloat(getComputedStyle(bar).paddingRight);
        return document.querySelector('.titlebar .tools')!.getBoundingClientRect().right <= end + 0.5;
      })).toBe(true);
      // At the lab PCs' three scales every command is on the bar (none on the » menu, D-158).
      const buttons = page.locator('.toolbar [data-unit]');
      expect(await buttons.count()).toBe(17); // Save Undo Redo, 8 tools, Run 1 Cycle N Cycles Reset, speed, Load Program
      for (const b of await buttons.all()) {
        await expect(b).toBeVisible();
        const box = (await b.boundingBox())!;
        expect(box.x + box.width).toBeLessThanOrEqual(size.width + 0.5);
      }
      await expect(page.locator('.toolbar .more')).toBeHidden();
      // The Canvas the widest.
      const canvas = (await page.locator('.canvaspanel').boundingBox())!;
      expect(canvas.width).toBeGreaterThan(size.width * 0.5);
    } finally {
      await r.close();
    }
  });
}

test('half a 1920 screen: no right column, Attributes a tab of the left panel; back in its column when wide again', async () => {
  const r = await launch({ width: 1600, height: 1000 });
  const { page } = r;
  const resize = (width: number, height: number) =>
    r.app.evaluate(({ BrowserWindow }, s) => BrowserWindow.getAllWindows()[0].setContentSize(s.width, s.height), { width, height });
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await expect(page.locator('section[aria-label="Attributes"] .aname')).toHaveText('main');
    await resize(960, 1032);
    await expect(page.locator('.shell')).toHaveClass(/narrow/);
    await expect(page.locator('.rightcol')).toBeHidden();
    await page.locator('.upper').getByRole('tab', { name: 'Attributes' }).click();
    await expect(page.locator('.upper .pbody:visible .aname')).toHaveText('main');
    expect(await clipped(page)).toEqual([]);
    expect(await brokenNames(page)).toEqual([]);
    // Every command on the bar or on its » menu (D-158: the » rule), none cut.
    await expect(page.locator('.toolbar [data-unit]')).toHaveCount(17);
    const over = await page.locator('.toolbar [data-unit][data-over]').evaluateAll((els) => els.map((e) => (e as HTMLElement).dataset.unit));
    for (const b of await page.locator('.toolbar [data-unit]:not([data-over])').all()) await expect(b).toBeVisible();
    if (over.length) {
      await page.locator('.toolbar .more').click();
      await expect(page.locator('.ovmenu.barmenu > button .label')).toHaveText(over as string[]);
      await page.keyboard.press('Escape');
    }
    // Wide again: Attributes in its own column, the left panel back on Components.
    await resize(1600, 1000);
    await expect(page.locator('.shell')).not.toHaveClass(/narrow/);
    await expect(page.locator('section[aria-label="Attributes"] .aname')).toHaveText('main');
    await expect(page.locator('.upper').getByRole('tab', { name: 'Attributes' })).toBeHidden();
    await expect(page.locator('.upper .pbody:visible .libgroup').first()).toBeVisible();
  } finally {
    await r.close();
  }
});

test('the empty panels say what fills them; only the Canvas has a character', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await page.getByRole('button', { name: /컴퓨터구조/ }).click();
    await page.getByRole('button', { name: /바로 시작/ }).click();
    await page.getByRole('button', { name: /새 회로/ }).click();
    const says: Record<string, string> = {};
    const read = async (panel: string, tab?: string) => {
      const p = page.locator(`section.${panel}`);
      if (tab) await p.getByRole('tab', { name: tab }).click();
      const body = p.locator('.pbody:visible');
      await expect(body.locator('.notice h3')).toBeVisible();
      await expect(body.locator('.notice p')).toBeVisible();
      expect(await body.locator('img.char').count(), `${panel} ${tab ?? ''}`).toBe(panel === 'canvaspanel' ? 1 : 0);
      says[tab ?? panel] = `${await body.locator('.notice h3').innerText()} / ${await body.locator('.notice p').innerText()}`.replace(/\u2060/g, '');
    };
    await read('canvaspanel');
    // Attributes: never empty with a circuit on show -- the circuit's own attributes (N-10, Y-05)
    if (await page.locator('.shell.narrow').count()) await page.locator('section.upper').getByRole('tab', { name: 'Attributes' }).click();
    await expect(page.locator('.pbody.attributes .aname')).toHaveText('main');
    await expect(page.locator('.pbody.attributes .ahead .badge')).toHaveText('Circuit');
    expect(await page.locator('.pbody.attributes img.char').count()).toBe(0);
    await read('lower', 'Tunnels');
    await read('lower', 'Minimap');
    await read('bottom', 'Messages');
    await read('bottom', 'Cycle View');
    await read('bottom', 'Console');
    expect(says).toEqual({
      canvaspanel: '빈 회로입니다 / 부품과 선을 놓으면 여기 Canvas에 그려집니다. 부품은 왼쪽 Components 목록에서 끌어 오거나 Ctrl+K 검색 창에서 찾아 놓습니다. 완성된 회로를 먼저 보려면 제목 줄 Menu 단추의 Help › Examples 메뉴에서 예제를 엽니다.',
      Tunnels: '터널이 없습니다 / 이 회로에 Tunnel을 놓으면 이름별로 여기에 모입니다.',
      Minimap: '회로 전체가 작게 나옵니다 / Canvas에 그린 회로의 전체 모습과 지금 보는 곳이 여기에 나옵니다.',
      Messages: '메시지가 없습니다 / 동작할 수 없는 연결(떠 있는 입력, 짝 없는 터널, 폭이 다른 선 …)이 있으면 여기에 나옵니다. 시뮬레이션 중에 생긴 E·X 값과 발진은 그 사이클과 함께 나옵니다.',
      'Cycle View': '아직 사이클이 없습니다 / 1 Cycle이나 Run으로 클럭을 진행하면 사이클마다 값이 여기에 쌓입니다.',
      Console: '아직 출력이 없습니다 / 회로의 Console 부품이 출력하면 여기에 나옵니다.',
    });
  } finally {
    await r.close();
  }
});

test('splitters: drag to resize, double-click for the default; the bottom panel folds to its head and back', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    const upper = page.locator('.upper');
    const w0 = (await upper.boundingBox())!.width;
    const split = (await page.locator('.shell > .splitter').first().boundingBox())!;
    await page.mouse.move(split.x + 4, split.y + 200);
    await page.mouse.down();
    await page.mouse.move(split.x + 64, split.y + 200, { steps: 4 });
    await page.mouse.up();
    await expect.poll(async () => Math.round((await upper.boundingBox())!.width - w0)).toBe(60);
    await page.mouse.dblclick(split.x + 64, split.y + 200);
    await expect.poll(async () => (await upper.boundingBox())!.width).toBe(w0);
    // The bottom panel: its head only, then back.
    const bottom = page.locator('section.bottom');
    const h0 = (await bottom.boundingBox())!.height;
    await bottom.getByRole('button', { name: 'Collapse' }).click();
    expect((await bottom.boundingBox())!.height).toBeLessThan(40);
    await expect(bottom.locator('.pbody:visible')).toHaveCount(0);
    await bottom.getByRole('tab', { name: 'Console' }).click(); // a tab opens it again
    expect((await bottom.boundingBox())!.height).toBe(h0);
    await expect(bottom.getByRole('button', { name: 'Collapse' })).toBeVisible();
  } finally {
    await r.close();
  }
});
