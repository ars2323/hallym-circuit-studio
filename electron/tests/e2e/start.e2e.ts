/* The first screen (src/renderer/app/start.ts, shared/welcome.ts): the card
   and its two steps -- the same box, the same pixels but for the choices;
   the four ways in (the two courses, a new circuit, a file); a .circ on the
   command line opens with no first screen at all. */

import { expect, test, type Page } from '@playwright/test';
import path from 'node:path';

import { answerOpen, DATAPATH, GATES, launch, repo, sample } from './harness.ts';
import { pixelDiff } from './png.ts';

// The card, and the part of it that is the step's own: the choices and the
// "← 처음으로" row, from the choices' top to that row's bottom, across the
// choices' width (2 px more on every side: a glyph's edge).
interface CardShot { png: Buffer; choices: { x: number; y: number; width: number; height: number } }
async function cardShot(page: Page): Promise<CardShot> {
  await page.mouse.move(1, 1);
  await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur());
  await page.waitForFunction(() => [...document.querySelectorAll('.wcard img')].every((i) => (i as HTMLImageElement).complete));
  await page.evaluate(() => document.fonts.ready);
  const choices = await page.evaluate(() => {
    const [c, a, b] = ['.wcard', '.wcard .actions', '.wcard .back'].map((s) => document.querySelector(s)!.getBoundingClientRect());
    return { x: a.left - c.left - 2, y: a.top - c.top - 2, width: a.width + 4, height: b.bottom - a.top + 4 };
  });
  return { png: await page.locator('.wcard').screenshot({ animations: 'disabled' }), choices };
}

// The same card twice in a row: Chromium may draw a scaled image at a lower
// quality first and at full quality a frame later.
async function settledShot(page: Page): Promise<CardShot> {
  let last = await cardShot(page);
  for (let i = 0; i < 20; i += 1) {
    await page.waitForTimeout(150);
    const next = await cardShot(page);
    if (Buffer.compare(last.png, next.png) === 0) return next;
    last = next;
  }
  throw new Error('the card never looked the same twice in a row');
}

test('the first screen: greeting, lead in two lines, two ways in; no toolbar', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await expect(page.locator('.wcard h1')).toHaveText('안녕하세요!');
    await expect(page.locator('.wcard p.lead')).toHaveText('논리 회로와 MIPS 프로세서를 그리고,클럭을 한 번씩 뛰며 동작을 보는 곳입니다.');
    expect(await page.locator('.wcard p.lead br').count()).toBe(1);
    await expect(page.locator('.action')).toHaveCount(2);
    await expect(page.locator('.action').nth(0)).toContainText('튜토리얼 보기');
    await expect(page.locator('.action').nth(0)).toContainText('예제를 열어한 단계씩 따라가 봅니다');
    await expect(page.locator('.action').nth(1)).toContainText('바로 시작');
    await expect(page.locator('.action').nth(1)).toContainText('새 회로를 그리거나가진 파일을 엽니다');
    await expect(page.locator('.wcard .back')).toHaveCSS('visibility', 'hidden');
    await expect(page.locator('.titlebar .toolbar')).toBeHidden();
    await expect(page.locator('.wcard img.char')).toHaveAttribute('src', /haram-hari-greeting\.png$/);
    // Every choice's two lines within its box.
    for (const sub of await page.locator('.action .sub').all()) {
      expect(await sub.evaluate((e) => e.scrollWidth <= e.clientWidth + 1)).toBe(true);
    }
  } finally {
    await r.close();
  }
});

test('step 2 is the same card: the same box and the same pixels, but for the choices and ← 처음으로', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const box1 = await page.locator('.wcard').boundingBox();
    const actions1 = await page.locator('.wcard .actions').boundingBox();
    const shot1 = await settledShot(page);
    for (const [way, choices] of [['튜토리얼 보기', ['논리설계 및 실험', '컴퓨터구조']], ['바로 시작', ['새 회로', '파일 열기']]] as const) {
      await page.getByRole('button', { name: new RegExp(way) }).click();
      await expect(page.locator('.action').nth(0)).toContainText(choices[0]);
      await expect(page.locator('.action').nth(1)).toContainText(choices[1]);
      await expect(page.locator('.wcard .back')).toHaveCSS('visibility', 'visible');
      expect(await page.locator('.wcard').boundingBox()).toEqual(box1);
      expect(await page.locator('.wcard .actions').boundingBox()).toEqual(actions1);
      for (const sub of await page.locator('.action .sub').all()) {
        expect(await sub.evaluate((e) => e.scrollWidth <= e.clientWidth + 1), await sub.innerText()).toBe(true);
      }
      const shot2 = await settledShot(page);
      expect(shot2.choices).toEqual(shot1.choices);
      expect(pixelDiff(shot1.png, shot2.png, [shot1.choices]), `${way}: the card changed outside its choices`).toBe('');
      await page.getByRole('button', { name: '← 처음으로' }).click();
      await expect(page.locator('.action').nth(0)).toContainText('튜토리얼 보기');
    }
    await page.getByRole('button', { name: /튜토리얼 보기/ }).click();
    await expect(page.locator('.action').nth(0)).toContainText('게이트와 선, 서브회로,클럭과 레지스터');
    await expect(page.locator('.action').nth(1)).toContainText('MIPS 부품, 프로그램 불러오기,사이클 보기');
    await page.getByRole('button', { name: '← 처음으로' }).click();
    await page.getByRole('button', { name: /바로 시작/ }).click();
    await expect(page.locator('.action').nth(0)).toContainText('빈 회로에서시작합니다');
    await expect(page.locator('.action').nth(1)).toContainText('가진 .circ 파일을엽니다 (Ctrl+O)');
  } finally {
    await r.close();
  }
});

for (const course of ['논리설계 및 실험', '컴퓨터구조']) {
  test(`튜토리얼 보기 → ${course}: a new circuit (the course's tutorial is N-18's)`, async () => {
    const r = await launch();
    const { page } = r;
    try {
      await page.getByRole('button', { name: /튜토리얼 보기/ }).click();
      await page.getByRole('button', { name: new RegExp(course) }).click();
      await expect(page.locator('.filebar .ptab')).toHaveText(['untitled.circ']);
      await expect(page.locator('.stage-welcome')).toBeHidden();
      await expect(page.locator('.canvas h3')).toHaveText('빈 회로입니다');
      await expect(page.locator('.toolbar')).toBeVisible();   // in the bar, or in its own row under it
    } finally {
      await r.close();
    }
  });
}

test('바로 시작 → 새 회로: an empty circuit from the engine, its main circuit on show', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await page.getByRole('button', { name: /바로 시작/ }).click();
    await page.getByRole('button', { name: /새 회로/ }).click();
    await expect(page.locator('.filebar .ptab')).toHaveText(['untitled.circ']);
    await expect(page.locator('.circuitbar .ptab')).toHaveText(['main']);
    await expect(page.locator('.titlebar .file')).toHaveText('untitled.circ');
    await expect(page.locator('.status')).toContainText('main · 부품 0개 · 선 0개');
    await expect(page.locator('.upper .pbody:visible .list > li')).not.toHaveCount(0); // Components: the engine's library
    await expect(page).toHaveTitle('untitled.circ — Hallym Circuit Studio');
  } finally {
    await r.close();
  }
});

test('바로 시작 → 파일 열기: the open dialog, then the engine opens it', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const file = sample(r.dir, DATAPATH);
    await answerOpen(r.app, file);
    await page.getByRole('button', { name: /바로 시작/ }).click();
    await page.getByRole('button', { name: /파일 열기/ }).click();
    await expect(page.locator('.filebar .ptab')).toHaveText(['demo-datapath.circ']);
    await expect(page.locator('.canvas h3')).toHaveText('이 회로에는 부품 35개와 선 41개가 있습니다');
    // Circuits: the file's three, main marked; one opens as a tab.
    await page.getByRole('tab', { name: 'Circuits' }).click();
    await expect(page.locator('.upper .pbody:visible .list > li')).toHaveText(['mainmain', 'regfile', 'alu']);
    if (!(await page.locator('.shell.narrow').count())) {
      await expect(page.locator('section.right h3')).toHaveText('고른 부품이 없습니다'); // Attributes stays in its column
    }
    await page.locator('.upper .pbody:visible .list > li', { hasText: 'regfile' }).getByRole('button').click();
    await expect(page.locator('.circuitbar .ptab')).toHaveText(['main', 'regfile']);
    await expect(page.locator('.status')).toContainText('regfile');
    // Tunnels: by label, with their counts.
    await page.locator('.circuitbar .ptab', { hasText: 'main' }).click();
    await expect(page.locator('.lower .list li').first()).toHaveText(/ALUOp\s*2/);
  } finally {
    await r.close();
  }
});

test('a .circ on the command line opens with no first screen', async () => {
  const dir = path.join(repo, 'tests/circ');
  const r = await launch(undefined, { args: [path.join(dir, path.basename(GATES))], waitFor: '.filebar .ptab' });
  const { page } = r;
  try {
    await expect(page.locator('.filebar .ptab')).toHaveText(['gates.circ']);
    expect(await page.evaluate(() => document.documentElement.dataset.startSeen ?? null)).toBeNull();
    await expect(page.locator('.stage-welcome')).toBeHidden();
    // Closing it: the first screen.
    await page.locator('.filebar .tabclose').click();
    await expect(page.locator('.wcard')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.dataset.startSeen)).toBe('true');
  } finally {
    await r.close();
  }
});

test('a file that cannot be opened: the window\'s own dialog, no character, the first screen stays', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await answerOpen(r.app, path.join(r.dir, 'missing.circ'));
    await page.keyboard.press('Control+o');
    const dialog = page.locator('dialog.ask');
    await expect(dialog).toContainText('파일을 열지 못했습니다');
    await expect(dialog).toContainText('missing.circ');
    await expect(dialog.locator('img.char')).toHaveCount(0);
    await dialog.getByRole('button', { name: '닫기' }).click();
    await expect(dialog).toHaveCount(0);
    await expect(page.locator('.wcard')).toBeVisible();
  } finally {
    await r.close();
  }
});
