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
    // The Canvas tools wait for their items: Edit and Poke are on (N-07), the others off.  N Cycles (N-07) and Load Program (N-16) are on.
    for (const name of ['Edit', 'Poke']) await expect(page.getByRole('radio', { name, exact: true })).toBeEnabled();
    for (const name of ['Wire', 'Text', 'Pin', 'Tunnel', 'Probe']) {
      await expect(page.getByRole('radio', { name, exact: true })).toBeDisabled();
    }
    // Signal Flow is a switch (Signal Flow on Click, N-15), on with a Canvas and pressed (v1's default)
    await expect(page.getByRole('button', { name: 'Signal Flow', exact: true })).toBeEnabled();
    await expect(page.getByRole('button', { name: 'Signal Flow', exact: true })).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByRole('button', { name: /N Cycles/ })).toBeEnabled();
    await expect(page.getByRole('button', { name: /Load Program/ })).toBeEnabled();
    await expect(page.getByRole('button', { name: /1 Cycle/ })).toBeEnabled();
  } finally {
    await r.close();
  }
});

test('view only until alpha.1 (D-154): a file open and drawn, the editing tools off, what works on; no engine or Java version in the status bar', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await expect(page.locator('.canvas .canvas-view canvas')).toBeVisible();
    await expect(page.locator('.status')).toContainText('35 components');
    // The Canvas's editing tools wait for N-08: shown, and off, even with a Canvas drawn.
    for (const name of ['Edit', 'Poke', 'Wire', 'Text', 'Pin', 'Tunnel', 'Probe']) {
      await expect(page.getByRole('radio', { name, exact: true })).toBeDisabled();
    }
    await expect(page.getByRole('button', { name: /N Cycles/ })).toBeDisabled();
    // What alpha.0 offers: the clock, Load Program, the Messages and the Cycle View.
    for (const name of [/^Run/, /^1 Cycle/, /^Reset/, /^Load Program/]) await expect(page.getByRole('button', { name })).toBeEnabled();
    await expect(page.getByRole('combobox', { name: 'Clock speed' })).toBeEnabled();
    const bottom = page.locator('section.bottom');
    for (const name of ['Messages', 'Cycle View']) {
      await bottom.getByRole('tab', { name }).click();
      await expect(bottom.getByRole('tab', { name })).toHaveAttribute('aria-selected', 'true');
    }
    await page.keyboard.press('F10');
    await expect(page.locator('.status')).toContainText('Cycle 1');
    // The versions are About's: the status bar has none of them (the fake engine's "none (fake engine, Node)" either).
    await expect(page.locator('.status')).not.toContainText(/Logisim|Java|engine/);
    await page.getByTitle('About').click();
    await expect(page.locator('dialog.about')).toContainText('Logisim 2.7.1 · Java none (fake engine, Node)');
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
    // the engine's libraries (tests/fixtures/library.json): the built-in ones, then the file's Hallym MIPS
    await expect(groups.filter({ hasText: 'Input/Output' })).toHaveCount(1);
    await expect(groups.last()).toContainText('Hallym MIPS');
  } finally {
    await r.close();
  }
});
