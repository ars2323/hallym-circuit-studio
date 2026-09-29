/* The two courses (A-08, D-168; src/renderer/app/logic/course.ts): what
   논리설계 및 실험 hides and 컴퓨터구조 shows in the parts list, its search,
   Ctrl+K, the toolbar and its » menu, the Cycle View's tabs, the status bar
   and Help › Examples; Radix Probe in both; the title bar's chip switches
   and nothing else changes; a file with MIPS-only parts opened in 논리설계
   (the strip and its one action); the course a .circ on the command line
   starts in. */

import { expect, test, type Page } from '@playwright/test';
import path from 'node:path';

import { partMiddle, rightClick } from './overlay-helpers.ts';
import { answerOpen, command, DATAPATH, GATES, launch, newCircuit, repo, resize, sample, type Running } from './harness.ts';

const LOGIC = '논리설계 및 실험';
const ARCH = '컴퓨터구조';

// The Hallym MIPS group of the Components list: its parts' names.
const mipsParts = (page: Page) => page.locator('.upper .libgroup', { has: page.locator('summary', { hasText: 'Hallym MIPS' }) }).locator('.list li');

// What the search box of the Components list finds for `text` (their names; none: []).
async function listSearch(page: Page, text: string): Promise<string[]> {
  const box = page.getByRole('searchbox', { name: 'Search parts' });
  await box.fill(text);
  await page.waitForTimeout(100);
  const found = await page.locator('.compresults li:visible').allInnerTexts();
  await box.fill('');
  return found.map((t) => t.split('\n')[0].trim());
}

// What Ctrl+K finds for `text`: its rows' names.
async function paletteSearch(page: Page, text: string): Promise<string[]> {
  await page.keyboard.press('Control+k');
  await page.locator('.palette .palinput').fill(text);
  await page.waitForTimeout(100);
  const names = await page.locator('.palette .palrow .palname').allInnerTexts();
  await page.keyboard.press('Escape');
  return names;
}

// The toolbar's commands on the bar and in its » menu (their names).
async function toolbarCommands(page: Page): Promise<string[]> {
  return page.locator('.toolbar [data-unit]').evaluateAll((els) => els.map((e) => (e as HTMLElement).dataset.unit ?? ''));
}

async function examples(page: Page): Promise<string[]> {
  await page.getByTitle('Menu').click();
  await page.locator('.ovmenu.barmenu').getByRole('menuitem', { name: /^Help/ }).click();
  await page.locator('.ovmenu').last().getByRole('menuitem', { name: /^Examples/ }).click();
  const names = await page.locator('.ovmenu').last().getByRole('menuitem').allInnerTexts();
  await page.keyboard.press('Escape');
  await page.keyboard.press('Escape');
  await page.keyboard.press('Escape');
  return names.map((n) => n.trim());
}

async function chooseCourse(page: Page, course: string): Promise<void> {
  await page.locator('.titlebar .coursechip').click();
  await page.getByRole('menuitemradio', { name: course }).click();
  await expect(page.locator('.titlebar .coursechip')).toHaveText(course);
}

// A part's right-click menu: its items' names (then closed).
async function partMenu(page: Page, name: string): Promise<string[]> {
  await rightClick(page, (await partMiddle(page, name)).at);
  const items = await page.locator('.ovmenu').first().locator('button .label').allInnerTexts();
  await page.keyboard.press('Escape');
  return items.map((x) => x.trim());
}

// What the window shows now, of everything the course decides.
async function shown(r: Running): Promise<Record<string, unknown>> {
  const { page } = r;
  return {
    mips: await mipsParts(page).allInnerTexts().then((x) => x.map((t) => t.trim())),
    list: await listSearch(page, 'instruction'),
    radix: await listSearch(page, 'radix'),
    palette: (await paletteSearch(page, 'console')).includes('Console'),
    loadCommand: (await paletteSearch(page, 'load program')).includes('Load Program…'),
    toolbar: (await toolbarCommands(page)).includes('Load Program…'),
    examples: await examples(page),
  };
}

test('a new circuit in each course: 논리설계 hides the MIPS-only parts, Load Program…, Registers | Memory | Instruction and the MIPS examples; Radix Probe in both; the chip switches only what is shown', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await newCircuit(r, LOGIC);
    await expect(page.locator('.titlebar .coursechip')).toHaveText(LOGIC);
    const logic = await shown(r);
    expect(logic).toEqual({
      mips: ['Radix Probe'], list: [], radix: ['Radix Probe'], palette: false, loadCommand: false, toolbar: false,
      examples: ['adder-1bit.circ', 'ripple-carry-4bit.circ', 'counter-4bit.circ'],
    });
    // nor on the » menu of a narrow bar
    await resize(r, { width: 700, height: 900 });
    await page.locator('.toolbar .more').click();
    await expect(page.locator('.ovmenu.barmenu')).not.toContainText('Load Program');
    await page.keyboard.press('Escape');
    await resize(r, { width: 1920, height: 1032 });
    // the chip: 컴퓨터구조 shows everything; the file stays as it was (the same tab, nothing to save)
    await chooseCourse(page, ARCH);
    const arch = await shown(r);
    expect(arch).toEqual({
      mips: ['Instruction Memory', 'Data Memory', 'Console', 'Radix Probe'], list: ['Instruction Memory'],
      radix: ['Radix Probe'], palette: true, loadCommand: true, toolbar: true,
      examples: ['demo-datapath.circ', 'console-demo.circ', 'stack-demo.circ'],
    });
    await expect(page.locator('.filebar .ptab')).toHaveText(['untitled.circ']);
    await expect(page.locator('.filebar .ptab.dirty')).toHaveCount(0);
    // and back
    await chooseCourse(page, LOGIC);
    expect(await shown(r)).toEqual(logic);
  } finally {
    await r.close();
  }
});

test('a file with MIPS-only parts in 논리설계: drawn and run as always, the strip says so with its one action; the right-click menus and the status bar follow the course', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const file = sample(r.dir, DATAPATH);
    await answerOpen(r.app, file);
    await page.getByRole('button', { name: new RegExp(LOGIC) }).click();
    await page.getByRole('button', { name: /바로 시작/ }).click();
    await page.getByRole('button', { name: /파일 열기/ }).click();
    await expect(page.locator('.canvas .canvas-view canvas')).toBeVisible();
    await expect(page.locator('.titlebar .coursechip')).toHaveText(LOGIC);
    const strip = page.locator('.courseband');
    await expect(strip.locator('.bandtext')).toHaveText('이 파일은 컴퓨터구조 부품(Hallym MIPS)을 씁니다');
    await expect(strip.getByRole('button')).toHaveText('컴퓨터구조로 바꾸기');
    // it runs: the clock, the Cycle View's table (no Registers | Memory | Instruction), no PC in the status bar
    await command(page, '1 Cycle');
    await command(page, '1 Cycle');
    await expect(page.locator('.status')).toContainText('Cycle 2');
    await expect(page.locator('.status')).not.toContainText('PC 0x');
    await expect(page.locator('.status .changed')).toHaveCount(0);
    await page.getByRole('tab', { name: 'Cycle View' }).click();
    await expect(page.locator('.cycleview .ctable')).toBeVisible();
    await expect(page.locator('.cycleview .cside')).toBeHidden();
    // the right-click menus: no Load Program… on the Instruction Memory, no Mark as Register File on the register file
    expect(await partMenu(page, 'Instruction Memory')).not.toContain('Load Program…');
    expect(await partMenu(page, 'regfile')).not.toContain('Mark as Register File');
    // the strip's action: 컴퓨터구조, the strip gone, the PC and the side there; the circuit and its cycle as they were
    await strip.getByRole('button').click();
    await expect(page.locator('.titlebar .coursechip')).toHaveText(ARCH);
    await expect(strip).toBeHidden();
    await expect(page.locator('.status')).toContainText('Cycle 2');
    await expect(page.locator('.status')).toContainText('PC 0x');
    await expect(page.locator('.status .changed')).toContainText('Changed');
    await expect(page.locator('.cycleview .cside')).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Registers' })).toBeVisible();
    expect(await partMenu(page, 'Instruction Memory')).toContain('Load Program…');
    expect(await partMenu(page, 'regfile')).toEqual(expect.arrayContaining([expect.stringMatching(/Register File/)]));
  } finally {
    await r.close();
  }
});

test('a file without MIPS-only parts in 논리설계: no strip', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await answerOpen(r.app, sample(r.dir, GATES));
    await page.getByRole('button', { name: new RegExp(LOGIC) }).click();
    await page.getByRole('button', { name: /바로 시작/ }).click();
    await page.getByRole('button', { name: /파일 열기/ }).click();
    await expect(page.locator('.filebar .ptab')).toHaveText(['gates.circ']);
    await page.waitForTimeout(300);
    await expect(page.locator('.courseband')).toBeHidden();
  } finally {
    await r.close();
  }
});

for (const [file, course] of [[DATAPATH, ARCH], [GATES, LOGIC]] as const) {
  test(`a .circ on the command line: ${path.basename(file)} starts in ${course} (a MIPS-only part or not); no strip`, async () => {
    const r = await launch(undefined, { args: [path.join(repo, file)], waitFor: '.filebar .ptab' });
    const { page } = r;
    try {
      await expect(page.locator('.titlebar .coursechip')).toHaveText(course);
      await expect(page.locator('.courseband')).toBeHidden();
      expect(await toolbarCommands(page)).toEqual(course === ARCH ? expect.arrayContaining(['Load Program…']) : expect.not.arrayContaining(['Load Program…']));
    } finally {
      await r.close();
    }
  });
}
