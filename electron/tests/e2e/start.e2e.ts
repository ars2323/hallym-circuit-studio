/* The first screen (src/renderer/app/start.ts, shared/welcome.ts): the card
   and its three steps (A-08: the course, then 튜토리얼 보기 / 바로 시작,
   then 새 회로 / 파일 열기) -- the same box, the same pixels but for the
   choices and ← 이전; the ways in; a .circ on the command line opens with
   no first screen at all, in the course its parts say. */

import { expect, test, type Page } from '@playwright/test';
import { writeFileSync } from 'node:fs';
import path from 'node:path';

import { answerOpen, answerSave, DATAPATH, GATES, launch, repo, sample, visibleCharacters } from './harness.ts';
import { pixelDiff } from './png.ts';

// The card, and the part of it that is the step's own: the choices and the
// "← 이전" row, from the choices' top to that row's bottom, across the
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

test('the first screen: greeting, lead in two lines, the course first; no toolbar, no course chip yet', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await expect(page.locator('.wcard h1')).toHaveText('안녕하세요!');
    await expect(page.locator('.wcard p.lead')).toHaveText('논리 회로와 MIPS 프로세서를 그리고,클럭을 한 번씩 뛰며 동작을 보는 곳입니다.');
    expect(await page.locator('.wcard p.lead br').count()).toBe(1);
    await expect(page.locator('.action')).toHaveCount(2);
    await expect(page.locator('.action').nth(0)).toContainText('논리설계 및 실험');
    await expect(page.locator('.action').nth(0)).toContainText('게이트와 선, 서브회로,클럭과 레지스터');
    await expect(page.locator('.action').nth(1)).toContainText('컴퓨터구조');
    await expect(page.locator('.action').nth(1)).toContainText('MIPS 부품, 프로그램 불러오기,사이클 보기');
    await expect(page.locator('.wcard .back')).toHaveCSS('visibility', 'hidden');
    await expect(page.locator('.titlebar .toolbar')).toBeHidden();
    await expect(page.locator('.titlebar .coursechip')).toBeHidden();
    await expect(page.locator('.wcard img.char')).toHaveAttribute('src', /haram-hari-greeting\.png$/);
    // The status bar's facts are names, in English.
    await expect(page.locator('.status > span').first()).toHaveText('Ready');
    // Every choice's two lines within its box.
    for (const sub of await page.locator('.action .sub').all()) {
      expect(await sub.evaluate((e) => e.scrollWidth <= e.clientWidth + 1)).toBe(true);
    }
  } finally {
    await r.close();
  }
});

test('steps 2 and 3 are the same card: the same box and the same pixels, but for the choices and ← 이전 (one step back)', async () => {
  const r = await launch();
  const { page } = r;
  try {
    // The video behind the card (backdrop.e2e.ts) held on one frame: the card's rounded corners show it.
    await page.waitForSelector('.wback.playing');
    await page.evaluate(() => new Promise<void>((done) => {
      const v = document.querySelector('.wback video') as HTMLVideoElement;
      v.pause();
      v.addEventListener('seeked', () => requestAnimationFrame(() => requestAnimationFrame(() => done())), { once: true });
      v.currentTime = 3;
    }));
    const box1 = await page.locator('.wcard').boundingBox();
    const actions1 = await page.locator('.wcard .actions').boundingBox();
    const shot1 = await settledShot(page);
    // step 1 (the course) → step 2 (튜토리얼 보기 · 바로 시작) → step 3 (새 회로 · 파일 열기), each against step 1
    for (const [press, choices] of [[/논리설계 및 실험/, ['튜토리얼 보기', '바로 시작']], [/바로 시작/, ['새 회로', '파일 열기']]] as const) {
      await page.getByRole('button', { name: press }).click();
      await expect(page.locator('.action').nth(0)).toContainText(choices[0]);
      await expect(page.locator('.action').nth(1)).toContainText(choices[1]);
      await expect(page.locator('.wcard .back')).toHaveCSS('visibility', 'visible');
      await expect(page.locator('.wcard .back')).toHaveText('← 이전');
      await expect(page.locator('.titlebar .coursechip')).toBeHidden();   // the card asks: no chip on the first screen
      expect(await page.locator('.wcard').boundingBox()).toEqual(box1);
      expect(await page.locator('.wcard .actions').boundingBox()).toEqual(actions1);
      for (const sub of await page.locator('.action .sub').all()) {
        expect(await sub.evaluate((e) => e.scrollWidth <= e.clientWidth + 1), await sub.innerText()).toBe(true);
      }
      const shot2 = await settledShot(page);
      expect(shot2.choices).toEqual(shot1.choices);
      expect(pixelDiff(shot1.png, shot2.png, [shot1.choices]), `${choices[0]}: the card changed outside its choices`).toBe('');
    }
    await expect(page.locator('.action').nth(0)).toContainText('빈 회로에서시작합니다');
    await expect(page.locator('.action').nth(1)).toContainText('가진 .circ 파일을엽니다 (Ctrl+O)');
    // ← 이전: one step back each time
    await page.getByRole('button', { name: '← 이전' }).click();
    await expect(page.locator('.action').nth(0)).toContainText('튜토리얼 보기');
    await expect(page.locator('.action').nth(0)).toContainText('예제를 열어한 단계씩 따라가 봅니다');
    await expect(page.locator('.action').nth(1)).toContainText('새 회로를 그리거나가진 파일을 엽니다');
    await page.getByRole('button', { name: '← 이전' }).click();
    await expect(page.locator('.action').nth(0)).toContainText('논리설계 및 실험');
    await expect(page.locator('.wcard .back')).toHaveCSS('visibility', 'hidden');
  } finally {
    await r.close();
  }
});

for (const [course, file, track] of [['논리설계 및 실험', 'tutorial-logic.circ', 'logic'], ['컴퓨터구조', 'tutorial-mips.circ', 'architecture']]) {
  test(`${course} → 튜토리얼 보기: that course's tutorial at once, on a copy of its example, the window in that course (N-18, D-161)`, async () => {
    const r = await launch();
    const { page } = r;
    try {
      await page.getByRole('button', { name: new RegExp(course) }).click();
      await page.getByRole('button', { name: /튜토리얼 보기/ }).click();
      await expect(page.locator('.filebar .ptab')).toHaveText([file]);
      await expect(page.locator('.titlebar .coursechip')).toHaveText(course);
      await expect(page.locator('.stage-welcome')).toBeHidden();
      await expect(page.locator('.tut-card .tut-count')).toHaveText(track === 'logic' ? '1 / 16' : '1 / 14');
      await expect(page.locator('dialog[open]')).toHaveCount(0);   // not asked again which course
    } finally {
      await r.close();
    }
  });
}

test('바로 시작 → 새 회로: an empty circuit from the engine, its main circuit on show', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await page.getByRole('button', { name: /컴퓨터구조/ }).click();
    await page.getByRole('button', { name: /바로 시작/ }).click();
    await page.getByRole('button', { name: /새 회로/ }).click();
    await expect(page.locator('.filebar .ptab')).toHaveText(['untitled.circ']);
    await expect(page.locator('.circuitbar .ptab')).toHaveText(['main']);
    await expect(page.locator('.titlebar .file')).toHaveAttribute('title', 'untitled.circ');   // the whole name (a narrow bar shortens the text, D-158)
    await expect(page.locator('.status')).toContainText('main · 0 components · 0 wires');
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
    await page.getByRole('button', { name: /컴퓨터구조/ }).click();
    await page.getByRole('button', { name: /바로 시작/ }).click();
    await page.getByRole('button', { name: /파일 열기/ }).click();
    await expect(page.locator('.filebar .ptab')).toHaveText(['demo-datapath.circ']);
    await expect(page.locator('.canvas .canvas-view canvas')).toBeVisible(); // drawn (N-05)
    await expect(page.locator('.status')).toContainText('35 components · 41 wires');
    // Circuits: the file's three, main marked; one opens as a tab.
    await page.getByRole('tab', { name: 'Circuits' }).click();
    await expect(page.locator('.upper .pbody:visible .list > li')).toHaveText(['main', 'regfile', 'alu']);
    // The main circuit: a mark with its name for screen readers, not the word "main" a second time.
    await expect(page.locator('.upper .pbody:visible .list > li').first().getByRole('img', { name: 'Main circuit' })).toBeVisible();
    await expect(page.locator('.upper .pbody:visible .list .mainmark')).toHaveCount(1);
    if (!(await page.locator('.shell.narrow').count())) {
      await expect(page.locator('section.right .aname')).toHaveText('main'); // Attributes stays in its column (the circuit's, N-10)
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

test('a .circ on the command line opens with no first screen, in 논리설계 및 실험 when it has no MIPS-only part', async () => {
  const dir = path.join(repo, 'tests/circ');
  const r = await launch(undefined, { args: [path.join(dir, path.basename(GATES))], waitFor: '.filebar .ptab' });
  const { page } = r;
  try {
    await expect(page.locator('.filebar .ptab')).toHaveText(['gates.circ']);
    await expect(page.locator('.titlebar .coursechip')).toHaveText('논리설계 및 실험');
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

test('a file that cannot be opened: the window\'s own words, the file\'s name alone, no character anywhere, the first screen stays', async () => {
  const r = await launch();
  const { page } = r;
  try {
    expect(await visibleCharacters(page)).toBe(1); // the greeting
    await answerOpen(r.app, path.join(r.dir, 'week3', 'lab3.circ'));
    await page.keyboard.press('Control+o');
    const dialog = page.locator('dialog.ask');
    await expect(dialog.locator('h2')).toHaveText('파일을 열지 못했습니다');
    await expect(dialog.locator('.askfile')).toHaveText('File: lab3.circ');
    // The folder by its name (never the whole path), the Open button by its key (D-158: #413's UI review).
    expect((await dialog.locator('.asktext > p').last().innerText()).replace(/\u2060/g, ''))
      .toBe('week3 폴더에 그 이름의 파일이 없습니다. 파일을 옮기거나 이름을 바꿨다면 제목 줄의 Open 단추(Ctrl+O)로 다시 골라 여세요.');
    // The engine's own words (English, with the whole path) are not the student's.
    const text = await dialog.innerText();
    expect(text).not.toContain('no such file');
    expect(text).not.toContain(r.dir);
    await expect(dialog.getByRole('button')).toHaveText(['Close']);
    // No character anywhere while the error is up; the greeting again after.
    expect(await visibleCharacters(page)).toBe(0);
    await dialog.getByRole('button', { name: 'Close' }).click();
    await expect(dialog).toHaveCount(0);
    await expect(page.locator('.wcard')).toBeVisible();
    expect(await visibleCharacters(page)).toBe(1);
  } finally {
    await r.close();
  }
});

test('a file Logisim cannot read, a file that cannot be saved: the same kind of dialog, the loader\'s words in the detail', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const notCirc = path.join(r.dir, 'notes.circ');
    writeFileSync(notCirc, 'not a circuit\n');
    await answerOpen(r.app, notCirc);
    await page.keyboard.press('Control+o');
    const dialog = page.locator('dialog.ask');
    await expect(dialog.locator('.askfile')).toHaveText('File: notes.circ');
    await expect(dialog).toContainText('Logisim이 이 파일을 회로로 읽지 못했습니다.');
    await expect(dialog.locator('.askdetail')).toContainText('does not appear to be a Logisim project file');
    await dialog.getByRole('button', { name: 'Close' }).click();
    // Saving where nothing can be written (a folder that is not there).
    await page.getByRole('button', { name: /논리설계 및 실험/ }).click();
    await page.getByRole('button', { name: /바로 시작/ }).click();
    await page.getByRole('button', { name: /새 회로/ }).click();
    await page.locator('.canvas h3').waitFor();
    await answerSave(r.app, path.join(r.dir, 'no-such-folder', 'mine.circ'));
    await page.keyboard.press('Control+s');
    await expect(dialog.locator('h2')).toHaveText('파일을 저장하지 못했습니다');
    await expect(dialog.locator('.askfile')).toHaveText('File: mine.circ');
    await expect(dialog).toContainText('Save As(Ctrl+Shift+S)');
    expect(await visibleCharacters(page)).toBe(0); // the Canvas's guide too
    await dialog.getByRole('button', { name: 'Close' }).click();
    await expect.poll(() => visibleCharacters(page)).toBe(1);
  } finally {
    await r.close();
  }
});
