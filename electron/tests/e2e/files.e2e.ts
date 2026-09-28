/* Files and tabs: one tab a file (the same file opened again goes to its
   tab), new circuits numbered, closing a tab shows its neighbour and the
   last one the first screen; the title bar and the window's title name the
   file; Undo and Redo reach the engine. */

import { expect, test } from '@playwright/test';

import { DATAPATH, GATES, launch, newCircuit, openFile, sample } from './harness.ts';

test('the same file opened twice: one tab, the second open goes to it', async () => {
  const r = await launch();
  const { page } = r;
  try {
    const datapath = sample(r.dir, DATAPATH);
    await openFile(r, datapath);
    await openFile(r, sample(r.dir, GATES));
    await expect(page.locator('.filebar .ptab.on')).toHaveText('gates.circ');
    await openFile(r, datapath);
    await expect(page.locator('.filebar .ptab')).toHaveText(['demo-datapath.circ', 'gates.circ']);
    await expect(page.locator('.filebar .ptab.on')).toHaveText('demo-datapath.circ');
    await expect(page).toHaveTitle('demo-datapath.circ — Hallym Circuit Studio');
    await expect(page.locator('.titlebar .file')).toHaveText('demo-datapath.circ');
  } finally {
    await r.close();
  }
});

test('new circuits: untitled.circ, untitled-2.circ (Ctrl+N); closing shows the left neighbour; the last closed, the first screen', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await newCircuit(r);
    await page.keyboard.press('Control+n');
    await page.keyboard.press('Control+n');
    await expect(page.locator('.filebar .ptab')).toHaveText(['untitled.circ', 'untitled-2.circ', 'untitled-3.circ']);
    await page.locator('.filebar .ptab', { hasText: 'untitled-2.circ' }).click();
    await page.getByRole('button', { name: 'Close untitled-2.circ' }).click();
    await expect(page.locator('.filebar .ptab')).toHaveText(['untitled.circ', 'untitled-3.circ']);
    await expect(page.locator('.filebar .ptab.on')).toHaveText('untitled.circ');
    await page.getByRole('button', { name: 'Close untitled.circ' }).click();
    await expect(page.locator('.filebar .ptab.on')).toHaveText('untitled-3.circ');
    await page.getByRole('button', { name: 'Close untitled-3.circ' }).click();
    await expect(page.locator('.wcard')).toBeVisible();
    await expect(page.locator('.action').first()).toContainText('튜토리얼 보기');
    await expect(page).toHaveTitle('Hallym Circuit Studio');
  } finally {
    await r.close();
  }
});

test('Undo and Redo go to the engine (a new circuit has nothing to undo: nothing to say)', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await newCircuit(r);
    await page.getByTitle('Undo (Ctrl+Z)').click();
    await page.keyboard.press('Control+y');
    await page.keyboard.press('Control+z');
    await expect(page.locator('.status .err')).toHaveCount(0);
    // The Canvas tools and N Cycles wait for their items: off.  Load Program is on (N-16).
    for (const name of ['Edit', 'Poke', 'Wire', 'Text', 'Pin', 'Tunnel', 'Probe', 'Signal Flow']) {
      await expect(page.getByRole('radio', { name, exact: true })).toBeDisabled();
    }
    await expect(page.getByRole('button', { name: /N Cycles/ })).toBeDisabled();
    await expect(page.getByRole('button', { name: /Load Program/ })).toBeEnabled();
    await expect(page.getByRole('button', { name: /1 Cycle/ })).toBeEnabled();
  } finally {
    await r.close();
  }
});

test('what the engine says about a file reaches the window: the loader\'s messages, a saved .circ that needs hcs-mips.jar', async () => {
  const r = await launch(undefined, { env: { FAKE_ENGINE_OPEN_MESSAGE: 'Unknown component: Foo', FAKE_ENGINE_MODE: 'needs-mips' } });
  const { page } = r;
  try {
    const file = sample(r.dir, DATAPATH);
    await openFile(r, file);
    await expect(page.locator('.status .err')).toHaveText('불러오며 알린 것 1개 — Unknown component: Foo');
    await page.keyboard.press('Control+s');
    await expect(page.locator('.status .ok')).toHaveText('저장했습니다 · demo-datapath.circ · 원조 Logisim 2.7.1에서 열려면 옆에 hcs-mips.jar가 있어야 합니다');
  } finally {
    await r.close();
  }
});

test('Components: this file\'s circuits first (under the file\'s name), then the libraries by their shown names', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    const groups = page.locator('.upper .libgroup summary');
    await expect(groups.first()).toContainText('demo-datapath.circ');
    await expect(groups.first().locator('.count')).toHaveText('3');
    await expect(groups.last()).toContainText('Input/Output');
  } finally {
    await r.close();
  }
});
