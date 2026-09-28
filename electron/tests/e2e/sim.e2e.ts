/* The simulation from the window (N-07, D-145; docs/interaction-parity.md
   I-63..I-76, I-148..I-154, I-159): Run/Stop, 1 Cycle, N Cycles and its
   dialog, Reset, the Tick Frequency, the Simulate keys, the band while the
   simulation is off, and the Poke tool on the Canvas -- with the fake engine
   answering demo-datapath from the engine-made fixture (its nets and values). */

import { expect, test } from '@playwright/test';

import { centerOn, clickAt, overlayOf, pagePoint, partBy, portValue } from './canvas-points.ts';
import { DATAPATH, launch, newCircuit, openFile, recordCalls, type Running, sample, sentCalls, visibleCharacters } from './harness.ts';
import { call, openFileIds } from './model.ts';

async function drawn(r: Running): Promise<void> {
  await r.page.locator('.canvas-view canvas').waitFor();
  await r.page.waitForFunction(() => {
    const c = (window as unknown as { __hcsCanvas?: { scene: { values: Map<string, string> } | null } }).__hcsCanvas;
    return !!c?.scene && c.scene.values.size > 0;
  });
}

const status = (r: Running) => r.page.locator('.status');

test('N Cycles: the dialog asks how many (10 at first), refuses 0, runs 100; Esc runs nothing; a long run shows what is left and Stop ends it (I-159)', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await newCircuit(r);
    await recordCalls(r.app);
    const dialog = page.locator('dialog.cycles');
    await page.getByRole('button', { name: /N Cycles/ }).click();
    await expect(dialog).toBeVisible();
    await expect(dialog.locator('h2')).toHaveText('N Cycles');
    await expect(dialog.getByRole('textbox', { name: 'Cycles' })).toHaveValue('10');
    await expect(dialog.getByRole('button')).toHaveText(['Cancel', 'Run']);
    await dialog.getByRole('textbox').fill('0');
    await page.keyboard.press('Enter');
    await expect(dialog.getByRole('alert')).toHaveText('1부터 100000까지의 수를 적으세요');
    await expect(dialog).toBeVisible();
    await dialog.getByRole('textbox').fill('100');
    await page.keyboard.press('Enter');
    await expect(dialog).toBeHidden();
    await expect(status(r)).toContainText('Cycle 100');
    expect((await sentCalls(r.app, 'sim.cycles')).map((c) => c.params.n)).toEqual([100]);
    // The count given last comes back; Esc is Cancel
    await page.getByRole('button', { name: /N Cycles/ }).click();
    await expect(dialog.getByRole('textbox')).toHaveValue('100');
    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
    expect((await sentCalls(r.app, 'sim.cycles')).length).toBe(1);
    // A long run: the status bar says what is left, Run is Stop, and Stop ends it (the engine stops asking for ticks)
    await page.getByRole('button', { name: /N Cycles/ }).click();
    await dialog.getByRole('textbox').fill('100000');
    await dialog.getByRole('button', { name: 'Run' }).click();
    await expect(status(r)).toContainText(/N Cycles · [\d,]+ left/);
    await expect(page.getByRole('button', { name: /^Stop/ })).toBeVisible();
    await page.getByRole('button', { name: /^Stop/ }).click();
    await expect(page.getByRole('button', { name: /^Run/ })).toBeVisible();
    await expect(status(r)).not.toContainText('left');
    const cycle = Number(/Cycle ([\d,]+)/.exec(await status(r).innerText())![1].replace(/,/g, ''));
    expect(cycle).toBeGreaterThan(100);
    expect(cycle).toBeLessThan(100100);
    const stop = (await sentCalls(r.app, 'sim.run')).at(-1)!;
    expect(stop.params.on).toBe(false);
  } finally {
    await r.close();
  }
});

test('Run and Stop, 1 Cycle, Reset, the Tick Frequency\'s seven speeds: facts in the status bar (I-159, I-161)', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await newCircuit(r);
    await recordCalls(r.app);
    await expect(status(r)).toContainText('Simulation On');
    await expect(status(r)).toContainText('Cycle 0');
    await expect(page.locator('.status .sim').last()).toHaveText('1 Hz');
    const speed = page.getByRole('combobox', { name: 'Clock speed' });
    expect(await speed.locator('option').allTextContents()).toEqual(['1 Hz', '4 Hz', '16 Hz', '64 Hz', '256 Hz', '1 kHz', '4 kHz']);
    await speed.selectOption('256');
    // the toolbar's key hints and the Run button's width are the same running as stopped (UI review of #431)
    const bar = () => page.evaluate(() => ({
      keys: [...document.querySelectorAll('.toolbar .btn kbd')].filter((k) => (k as HTMLElement).offsetWidth > 0).map((k) => k.textContent),
      run: (document.querySelector('.toolbar .btn .label.swap') as HTMLElement).closest('button')!.getBoundingClientRect().width,
    }));
    const stopped = await bar();
    expect(stopped.keys).toEqual(expect.arrayContaining(['F5', 'F10']));
    await page.keyboard.press('F5');
    await expect(page.locator('.status .run')).toHaveText('Running (256 Hz)');
    expect((await sentCalls(r.app, 'sim.run')).at(-1)!.params).toMatchObject({ on: true, hz: 256 });
    await expect(page.getByRole('button', { name: /^Stop\s*F5$/ })).toBeVisible();   // the hidden Run is no part of its name
    expect(await bar()).toEqual(stopped);
    await page.getByRole('button', { name: /^Stop/ }).click();
    await expect(page.locator('.status .sim').last()).toHaveText('256 Hz');
    await page.keyboard.press('F10');
    await expect(status(r)).toContainText('Cycle 1');
    await page.getByRole('button', { name: /1 Cycle/ }).click();
    await expect(status(r)).toContainText('Cycle 2');
    await page.getByRole('button', { name: /Reset/ }).click();
    await expect(status(r)).toContainText('Cycle 0');
  } finally {
    await r.close();
  }
});

// N-22, D-160: while the clock runs the engine says the count up to once a frame; only the status bar is drawn again
// (every panel each frame made Run at 4 kHz stutter on ref-mips).  A whole-window render makes the Run button's
// contents anew; the status bar is made anew by either.  Marks on both tell which ran.
test('Run: the engine\'s count each frame redraws the status bar only; Stop redraws the window (N-22)', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await newCircuit(r);
    await page.keyboard.press('F5');
    await expect(page.locator('.status .run')).toHaveText('Running (1 Hz)');
    const [fileId] = await openFileIds(r.app);
    type Marked = HTMLElement & { __kept?: boolean };
    const mark = () => page.evaluate(() => {
      (document.querySelector('.toolbar .btn .label.swap') as Marked).__kept = true;
      (document.querySelector('.status .run') as Marked).__kept = true;
    });
    const kept = () => page.evaluate(() => ({
      toolbar: (document.querySelector('.toolbar .btn .label.swap') as Marked).__kept === true,
      status: (document.querySelector('.status .sim') as Marked | null)?.__kept === true || (document.querySelector('.status .run') as Marked | null)?.__kept === true,
    }));
    const tell = (st: Record<string, unknown>) => r.app.evaluate(({ BrowserWindow }, x) => {
      BrowserWindow.getAllWindows()[0].webContents.send('engine:notify', 'sim.state', x);
    }, { fileId, running: true, ticking: true, oscillating: false, hz: 1, cyclesLeft: 0, ...st });
    await mark();
    for (let c = 1; c <= 60; c += 1) await tell({ cycle: c });
    await expect.poll(kept, { message: 'count-only states: the status bar made anew, the toolbar kept' }).toEqual({ toolbar: true, status: false });
    await expect(page.locator('.status .run')).toHaveText('Running (1 Hz)');
    await mark();
    await tell({ cycle: 61, ticking: false });
    await expect(page.getByRole('button', { name: /^Run\s*F5$/ })).toBeVisible();
    expect(await kept(), 'Run is Stop again: the window drawn again').toEqual({ toolbar: false, status: false });
  } finally {
    await r.close();
  }
});

test('Simulate keys: Ctrl+E off and on with the band, Ctrl+T half a cycle, Ctrl+I only while off, Ctrl+R resets and never reloads (I-148..I-152, I-206)', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await newCircuit(r);
    await recordCalls(r.app);
    await page.keyboard.press('Control+e');
    await expect(page.locator('.simband')).toHaveText('시뮬레이션이 꺼져 있어 값이 바뀌지 않습니다 · Ctrl+E 키로 다시 켭니다');
    await expect(page.locator('.status .sim.err')).toHaveText('Simulation Off');
    expect(await visibleCharacters(page)).toBe(0);
    await page.keyboard.press('Control+i');
    expect((await sentCalls(r.app, 'sim.step')).length).toBe(1);
    await page.keyboard.press('Control+e');
    await expect(page.locator('.simband')).toBeHidden();
    await page.keyboard.press('Control+i');
    await expect(page.locator('.status .err').last()).toHaveText('Step Simulation: 시뮬레이션이 켜져 있을 때는 할 수 없습니다. Ctrl+E 키로 끈 뒤 하세요');
    await page.keyboard.press('Control+t');
    await page.keyboard.press('Control+t');
    await expect(status(r)).toContainText('Cycle 1');
    expect((await sentCalls(r.app, 'sim.tick')).length).toBe(2);
    const marker = await page.evaluate(() => { (window as unknown as { __notReloaded: boolean }).__notReloaded = true; return true; });
    await page.keyboard.press('Control+r');
    await expect(status(r)).toContainText('Cycle 0');
    await page.keyboard.press('Control+Shift+R');
    await page.waitForTimeout(300);
    expect(await page.evaluate(() => (window as unknown as { __notReloaded?: boolean }).__notReloaded)).toBe(marker);
  } finally {
    await r.close();
  }
});

test('Poke: a press and a release apart -- a button held down gets its release only when the mouse lets go (I-66, I-67, I-76)', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await newCircuit(r);
    const fileId = 'f1';   // a fresh engine's first file
    const lib = await call<{ lib: string | null; tools: { circuitId?: string }[] }[]>(page, 'model.library', { fileId });
    const circuitId = lib[0].tools[0].circuitId!;
    await call(page, 'edit.addComponent', { fileId, circuitId, lib: 'I/O', name: 'Button', loc: [200, 200] });
    await page.locator('.canvas-view canvas').waitFor();
    await recordCalls(r.app);
    await page.getByRole('radio', { name: 'Poke' }).click();
    const button = (await partBy(page, '', 'Button'))!;
    await centerOn(page, [button.loc[0], button.loc[1]], 2);
    const at = await pagePoint(page, [button.bounds[0] + 10, button.bounds[1] + 10]);
    await page.mouse.move(at.x, at.y);
    await page.mouse.down();
    await expect.poll(async () => (await sentCalls(r.app, 'sim.poke')).map((c) => c.params.action)).toEqual(['press']);
    await page.waitForTimeout(200);
    expect((await sentCalls(r.app, 'sim.poke')).length).toBe(1);   // held down: no release yet
    await page.mouse.up();
    await expect.poll(async () => (await sentCalls(r.app, 'sim.poke')).map((c) => c.params.action)).toEqual(['press', 'release']);
    expect((await sentCalls(r.app, 'sim.poke'))[1].params).toMatchObject({ componentId: button.id, at: [button.bounds[0] + 10, button.bounds[1] + 10] });
  } finally {
    await r.close();
  }
});

test('Poke: an input pin flips the bit under the pointer, a clock flips, a wire shows its value, a register takes hex digits, the lens goes inside (I-63, I-65, I-68, I-74, I-75)', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, DATAPATH));
    await drawn(r);
    await recordCalls(r.app);
    await page.getByRole('radio', { name: 'Poke' }).click();
    await expect(page.getByRole('radio', { name: 'Poke' })).toHaveAttribute('aria-checked', 'true');
    expect((await overlayOf(page)).magnifiers).toBe(true);
    // RegWrite: a 1-bit input pin
    const regWrite = (await partBy(page, 'RegWrite', 'Pin'))!;
    await centerOn(page, [regWrite.bounds[0], regWrite.bounds[1]], 2);
    const before = await portValue(page, regWrite.id);
    await clickAt(page, [regWrite.bounds[0] + 10, regWrite.bounds[1] + 10]);
    await expect.poll(() => portValue(page, regWrite.id)).toBe(before === '1' ? '0' : '1');
    const pokes = await sentCalls(r.app, 'sim.poke');
    expect(pokes.map((c) => c.params.action)).toEqual(['press', 'release']);
    expect(pokes[0].params).toMatchObject({ componentId: regWrite.id, at: [regWrite.bounds[0] + 10, regWrite.bounds[1] + 10] });
    // ALUOp: 2 bits; the left half is bit 1 (PinPoker.getBit)
    const aluOp = (await partBy(page, 'ALUOp', 'Pin'))!;
    await centerOn(page, [aluOp.bounds[0], aluOp.bounds[1]], 2);
    const v0 = (await portValue(page, aluOp.id))!;
    await clickAt(page, [aluOp.bounds[0] + 5, aluOp.bounds[1] + 10]);
    await expect.poll(() => portValue(page, aluOp.id)).toBe(`${v0[0] === '1' ? '0' : '1'}${v0[1]}`);
    // the clock
    const clock = (await partBy(page, '', 'Clock'))!;
    await centerOn(page, [clock.bounds[0], clock.bounds[1]], 2);
    const c0 = await portValue(page, clock.id);
    await clickAt(page, [clock.bounds[0] + 10, clock.bounds[1] + 10]);
    await expect.poll(() => portValue(page, clock.id)).toBe(c0 === '1' ? '0' : '1');
    // a wire: the value box (binary / signed decimal) until the next press elsewhere
    const wire = await page.evaluate(() => {
      const c = (window as unknown as { __hcsCanvas: { scene: { wires: Map<string, { a: number[]; b: number[] }> } } }).__hcsCanvas;
      const w = [...c.scene.wires.values()].find((x) => Math.abs(x.a[0] - x.b[0]) >= 40 && x.a[1] === x.b[1])!;
      return [(w.a[0] + w.b[0]) / 2, w.a[1]] as [number, number];
    });
    await centerOn(page, wire, 1);
    await clickAt(page, wire);
    const box = (await overlayOf(page)).valueBox as { net: string; text: string } | null;
    expect(box?.net).toBeTruthy();
    // the PC register: a caret that takes hex digits (the red box), dropped by a press on nothing
    const pc = (await partBy(page, 'PC', 'Register'))!;
    await centerOn(page, [pc.bounds[0], pc.bounds[1]], 2);
    await clickAt(page, [pc.bounds[0] + 15, pc.bounds[1] + 20]);
    await expect.poll(async () => (await overlayOf(page)).caret).toBe(pc.id);
    expect((await overlayOf(page)).valueBox).toBeNull();
    await page.keyboard.type('1f');
    await expect.poll(() => portValue(page, pc.id)).toBe('00000000000000000000000000011111');
    expect((await sentCalls(r.app, 'sim.pokeKey')).map((c) => c.params.key)).toEqual(['1', 'f']);
    await clickAt(page, [pc.bounds[0] - 30, pc.bounds[1] - 30]);
    await expect.poll(async () => (await overlayOf(page)).caret ?? null).toBeNull();
    expect((await sentCalls(r.app, 'sim.pokeStop')).length).toBe(1);
    // the lens: a double click on it goes into that regfile instance
    const regfile = (await partBy(page, '', 'regfile'))!;
    const cx = regfile.bounds[0] + Math.floor(regfile.bounds[2] / 2), cy = regfile.bounds[1] + Math.floor(regfile.bounds[3] / 2);
    await centerOn(page, [cx, cy], 1);
    await clickAt(page, [cx, cy], { count: 2 });
    await expect(page.locator('.canvas-crumbs')).toContainText('regfile');
    // Edit again: no lens, no caret
    await page.getByRole('radio', { name: 'Edit' }).click();
    expect((await overlayOf(page)).magnifiers).toBeFalsy();
    expect(await pagePoint(page, [0, 0])).toBeTruthy();
  } finally {
    await r.close();
  }
});
