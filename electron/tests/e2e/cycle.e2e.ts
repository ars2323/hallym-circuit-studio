/* The Cycle View (N-14): the cycle table, a past cycle and back, Run Until,
   the Registers, Memory and Instruction panels (Hallym MIPS's), Mark as PC,
   the register file and its mapping, the status bar's cycle facts -- with
   the fake engine (tests/fake-engine/fake-record.ts runs the recursive
   factorial one instruction a cycle for a file with an Instruction
   Memory). */

import { expect, test, type Page } from '@playwright/test';

import { DATAPATH, GATES, launch, openFile, sample, statusText, type Running } from './harness.ts';

async function cycleView(r: Running): Promise<Page> {
  await openFile(r, sample(r.dir, DATAPATH));
  await r.page.locator('section.bottom').getByRole('tab', { name: 'Cycle View' }).click();
  return r.page;
}

// The side's tab (in a narrow window the table is the first tab, Cycles).
async function sideTab(page: Page, name: 'Cycles' | 'Registers' | 'Memory' | 'Instruction'): Promise<void> {
  const tab = page.locator('.cside').getByRole('tab', { name });
  if (name === 'Cycles' && !(await tab.isVisible())) return;
  await tab.click();
}

async function cycles(page: Page, n: number): Promise<void> {
  const before = Number(/Cycle (\d+)/.exec(await statusText(page))?.[1] ?? 0);
  for (let i = 0; i < n; i += 1) await page.getByRole('button', { name: /1 Cycle/ }).click();
  await expect(page.locator('.status')).toContainText(`Cycle ${before + n}`);
}

const side = (page: Page) => page.locator('.cside');

test('the table follows the clock; a column shows a past cycle; Latest comes back; the status bar says which', async () => {
  const r = await launch();
  try {
    const page = await cycleView(r);
    // A circuit with an Instruction Memory: the table from cycle 0 (PC and the instruction to run).
    await expect(page.locator('.ctable tr.hcycle th[data-cycle]')).toHaveText(['0']);
    await expect(page.locator('.ctable tr.hpc td')).toHaveText(['0x00400024']);
    await expect(page.locator('.ctable tr.hins td')).toHaveText(['lui $1, 32767']);
    await cycles(page, 6);
    await expect(page.locator('.cbar .cpos')).toHaveText('Cycle 6');
    // As many cycles as the width holds, the latest last (the table follows the clock).
    await expect(page.locator('.ctable tr.hcycle th[data-cycle]').last()).toHaveText('6');
    expect(await page.locator('.ctable tr.hcycle th[data-cycle]').count()).toBeGreaterThanOrEqual(3);
    await expect(page.locator('.ctable tr.hcycle th.on')).toHaveText('6');
    await expect(page.locator('.ctable tr.hins td[data-cycle="3"]')).toHaveText('jal 0x00400058 [fact]');
    expect(await statusText(page)).toMatch(/Cycle 6\s+PC 0x00400060/);
    // Cycle 3: the whole circuit shows it (the engine swaps the state); the status bar says "3 / 6".
    await page.locator('.ctable tr.hcycle th[data-cycle="3"]').click();
    await expect(page.locator('.cbar .cpos')).toHaveText('Cycle 3 / 6');
    await expect(page.locator('.cbar .cpast')).toBeVisible();
    await expect(page.locator('.status .warn')).toHaveText('Cycle 3 / 6');
    await expect(page.locator('.ctable tr.hcycle th.on')).toHaveText('3');
    await page.getByRole('button', { name: 'Previous Cycle' }).click();
    await expect(page.locator('.cbar .cpos')).toHaveText('Cycle 2 / 6');
    await page.getByRole('button', { name: 'Latest Cycle' }).click();
    await expect(page.locator('.cbar .cpos')).toHaveText('Cycle 6');
    await expect(page.locator('.cbar .cpast')).toBeHidden();
    await expect(page.getByRole('button', { name: 'Latest Cycle' })).toBeDisabled();
    // Next at the latest cycle runs one.
    await page.getByRole('button', { name: 'Next Cycle' }).click();
    await expect(page.locator('.cbar .cpos')).toHaveText('Cycle 7');
    // From a past cycle the run goes on from there: the later cycles are gone.
    await page.locator('.ctable tr.hcycle th[data-cycle="4"]').click();
    await expect(page.locator('.cbar .cpos')).toHaveText('Cycle 4 / 7');
    await page.getByRole('button', { name: /1 Cycle/ }).click();
    await expect(page.locator('.cbar .cpos')).toHaveText('Cycle 5');
    await expect(page.locator('.ctable tr.hcycle th[data-cycle]').last()).toHaveText('5');
  } finally {
    await r.close();
  }
});

test('rows: a signal added from the engine, a wave for a bit, a bus in hex, Show Bits, Remove', async () => {
  const r = await launch();
  try {
    const page = await cycleView(r);
    // (Add to Cycle View is the Canvas's right-click, N-05/N-10: the same engine call.)
    await page.evaluate(async () => {
      const app = (window as unknown as { app: { call(m: string, p: unknown): Promise<unknown> } }).app;
      await app.call('record.addRow', { fileId: 'f1', circuitId: 'c1', at: [300, 200] });
      await app.call('record.addRow', { fileId: 'f1', circuitId: 'c1', at: [1240, 500] });
    });
    await cycles(page, 3);
    const rows = page.locator('.ctable tbody tr.crow');
    await expect(rows.locator('.rname')).toHaveText(['PC', 'clk']);
    await expect(rows.nth(0).locator('td[data-cycle="3"]')).toHaveText('0x00400030');
    await expect(rows.nth(1).locator('td.wavecell[data-cycle="3"] .w')).toHaveCount(2);
    await rows.nth(0).locator('th').click({ button: 'right' });
    await page.locator('.popmenu').getByRole('menuitem', { name: 'Show Bits' }).click();
    await expect(page.locator('.ctable tr.bit')).toHaveCount(32);
    await rows.nth(0).locator('th').click({ button: 'right' });
    await page.locator('.popmenu').getByRole('menuitem', { name: 'Remove from Cycle View' }).click();
    await expect(page.locator('.ctable tbody tr.crow:not(.bit) .rname')).toHaveText(['clk']);
  } finally {
    await r.close();
  }
});

test('Registers, Memory and Instruction follow the cycle on show (Hallym MIPS\'s panels)', async () => {
  const r = await launch();
  try {
    const page = await cycleView(r);
    await cycles(page, 4);
    await sideTab(page, 'Registers');
    // Registers: Hex, Dec, Bin at once; Hallym MIPS's bands; PC in Special; the changed row in yellow.
    const regs = side(page).locator('.regs');
    await expect(regs.locator('.rhead span')).toHaveText(['Name', 'Hex', 'Dec', 'Bin']);
    await expect(regs.locator('.rgroup .gname').first()).toHaveText('Special');
    const a0 = regs.locator('.rrow[data-reg="$a0"]');
    await expect(a0.locator('.hex')).toHaveText('0x00000006');
    await expect(a0.locator('.dec')).toHaveText('6');
    await expect(a0.locator('.bin span')).toHaveCount(8);
    // Cycle 4: jal fact ran in the cycle before -- $ra changed (PC moves every cycle and is never the yellow row).
    await expect(regs.locator('.rrow.chg')).toHaveAttribute('data-reg', '$ra');
    await expect(regs.locator('.rrow.chg .hex')).toHaveText('0x00400034');
    // Memory: one table, the data from 0x10010000 with its label, the stack with $sp.
    await side(page).getByRole('tab', { name: 'Memory' }).click();
    const data = side(page).locator('.data');
    await expect(data.locator('.dsec').first()).toContainText('User data');
    await expect(data.locator('.dtags .dlabel').first()).toContainText('msg');
    await expect(data.locator('.drow').first().locator('.dval').first()).toHaveText('3d202136');
    await expect(data.locator('.dsec.dsec-stack')).toContainText('$sp depth');
    await expect(data.locator('.dtags .dptr')).toContainText('$sp →');
    // Instruction: the word in Hallym MIPS's coloured fields.
    await side(page).getByRole('tab', { name: 'Instruction' }).click();
    const insp = side(page).locator('.insp');
    await expect(insp.locator('.ihead .dis')).toHaveText('addi $29, $29, -8');
    await expect(insp.locator('.badge')).toHaveText('I');
    await expect(insp.locator('.fbox .fname')).toHaveText(['opcode', 'rs', 'rt', 'immediate']);
    await expect(insp.locator('.fbox.f-immediate .fmean')).toHaveText('0xfff8');
    // A past cycle: the panels show it.
    await page.getByRole('button', { name: 'Previous Cycle' }).click();
    await expect(page.locator('.cbar .cpos')).toHaveText('Cycle 3 / 4');
    await expect(insp.locator('.ihead .dis')).toHaveText('jal 0x00400058 [fact]');
  } finally {
    await r.close();
  }
});

test('Registers: 10-digit and negative values whole in Hex, Dec and Bin -- Special, Return values, Pointers, Saved', async () => {
  const r = await launch(undefined, { env: { FAKE_ENGINE_MODE: 'wide-registers' } });
  try {
    const page = await cycleView(r);
    await cycles(page, 12);
    await sideTab(page, 'Registers');
    const regs = side(page).locator('.regs');
    await expect(regs.locator('.rrow[data-reg="$s7"] .dec')).toHaveText('-2147483648');
    await expect(regs.locator('.rrow[data-reg="$s6"] .dec')).toHaveText('-1');
    await expect(regs.locator('.rrow[data-reg="$sp"] .dec')).toHaveText(/^2147479\d{3}$/);   // ten digits
    await expect(regs.locator('.rgroup .gname')).toHaveText(['Special', 'Return values', 'Arguments', 'Temporaries', 'Saved', 'Pointers', 'Return address', 'Reserved']);
    // Every cell the width shows is whole: nothing cut by the columns' overflow.
    const cut = await page.evaluate(() => {
      const out: string[] = [];
      for (const row of document.querySelectorAll<HTMLElement>('.cside .regs .rrow')) {
        for (const cell of row.querySelectorAll<HTMLElement>('.hex, .dec, .bin, .rn')) {
          if (!cell.checkVisibility()) continue;
          if (cell.scrollWidth > cell.clientWidth + 1) out.push(`${row.dataset.reg} ${cell.className}: ${cell.textContent}`);
        }
      }
      return out;
    });
    expect(cut).toEqual([]);
  } finally {
    await r.close();
  }
});

test('Run Until: the condition in the window\'s dialog, the stop in the status bar\'s words, Stop while it runs', async () => {
  const r = await launch();
  try {
    const page = await cycleView(r);
    await page.getByRole('button', { name: 'Run Until…' }).click();
    const dialog = page.locator('dialog.ask');
    await expect(dialog.locator('h2')).toHaveText('Run Until');
    const run = dialog.getByRole('button', { name: 'Run' });
    await expect(run).toBeDisabled();           // PC Is, nothing typed
    await expect(dialog.locator('.cfield > span')).toHaveText(['Condition', 'Value', 'Row', 'Max Cycles']);
    await dialog.getByLabel('Value').fill('0x00400058');
    await expect(run).toBeEnabled();
    await run.click();
    await expect(page.locator('.status .ok')).toHaveText('사이클 4에서 멈췄습니다: PC 0x00400058');
    await expect(page.locator('.cbar .cpos')).toHaveText('Cycle 4');
    // Halt or Exit
    await page.getByRole('button', { name: 'Run Until…' }).click();
    await dialog.getByLabel('Condition').selectOption({ label: 'Halt or Exit' });
    await expect(dialog.getByLabel('Value')).toBeHidden();
    await dialog.getByRole('button', { name: 'Run' }).click();
    await expect(page.locator('.status .ok')).toContainText('에서 멈췄습니다: halt 또는 exit');
    // A wrong instruction name keeps Run off and says why.
    await page.getByRole('button', { name: 'Run Until…' }).click();
    await dialog.getByLabel('Condition').selectOption({ label: 'Next Instruction Is' });
    await dialog.getByLabel('Value').fill('1beq');
    await expect(dialog.getByRole('button', { name: 'Run' })).toBeDisabled();
    await expect(dialog.locator('.cwhy')).toHaveText('명령어 이름을 적어 주세요. 예: beq, jal, syscall');
    await dialog.getByRole('button', { name: 'Cancel' }).click();
  } finally {
    await r.close();
  }
  // A long run: Stop in the bar, the clock's buttons wait.
  const s = await launch(undefined, { env: { FAKE_ENGINE_MODE: 'slow-until' } });
  try {
    const page = await cycleView(s);
    await page.getByRole('button', { name: 'Run Until…' }).click();
    await page.locator('dialog.ask').getByLabel('Condition').selectOption({ label: 'E or X Appears' });
    await page.locator('dialog.ask').getByRole('button', { name: 'Run' }).click();
    await expect(page.locator('.cbar').getByRole('button', { name: 'Stop' })).toBeVisible();
    await expect(page.locator('.status .run')).toHaveText('Running (Run Until)');
    await expect(page.getByRole('button', { name: /1 Cycle/ })).toBeDisabled();
    await page.locator('.cbar').getByRole('button', { name: 'Stop' }).click();
    await expect(page.locator('.status')).toContainText('사이클 1에서 멈췄습니다.');
    await expect(page.locator('.cbar').getByRole('button', { name: 'Run Until…' })).toBeVisible();
    await expect(page.getByRole('button', { name: /1 Cycle/ })).toBeEnabled();
  } finally {
    await s.close();
  }
});

test('Mark as PC from the Registers panel; the register file and its mapping; the file becomes unsaved', async () => {
  const r = await launch();
  try {
    const page = await cycleView(r);
    await cycles(page, 1);
    await sideTab(page, 'Registers');
    const pc = side(page).locator('.rrow[data-reg="PC"]');
    await expect(pc.locator('.rn')).toContainText('PC');
    await pc.click({ button: 'right' });
    await page.locator('.popmenu').getByRole('menuitem', { name: 'Mark as PC' }).click();
    await expect(pc.locator('.pcmark')).toHaveText('PC');
    await expect(page.locator('.filebar .ptab .dirty')).toHaveCount(1);
    // Mark as Register File…: choose the subcircuit
    await side(page).getByRole('button', { name: 'Mark as Register File…' }).click();
    const dialog = page.locator('dialog.ask');
    await expect(dialog.locator('h2')).toHaveText('레지스터 파일 고르기');
    await dialog.getByRole('button', { name: 'Mark as Register File' }).click();
    await expect(side(page).getByRole('button', { name: 'Register Mapping…' })).toBeVisible();
    await expect(side(page).locator('.rrow[data-reg="$zero"]')).toHaveCount(1);
    await side(page).getByRole('button', { name: 'Register Mapping…' }).click();
    await expect(dialog.locator('h2')).toHaveText('Register Mapping · regfile');
    await expect(dialog.locator('select')).toHaveCount(32);
    await expect(dialog.getByLabel('$sp')).toHaveValue('700,680');   // a Register by its place (v1's regmap)
    await dialog.getByRole('button', { name: 'Apply' }).click();
    await expect(dialog).toHaveCount(0);
    // A register row's menu: the register file's commands by their glossary names
    await side(page).locator('.rrow[data-reg="$sp"]').click({ button: 'right' });
    await expect(page.locator('.popmenu [role="menuitem"]')).toHaveText(['Register Mapping…', 'Unmark Register File']);
    await page.locator('.popmenu').getByRole('menuitem', { name: 'Unmark Register File' }).click();
    await expect(side(page).getByRole('button', { name: 'Mark as Register File…' })).toBeVisible();
  } finally {
    await r.close();
  }
});

test('a circuit with no clock run yet keeps the tab\'s word; a narrow window puts the table in a Cycles tab', async () => {
  const r = await launch({ width: 960, height: 1032 });
  try {
    await openFile(r, sample(r.dir, GATES));
    const page = r.page;
    await page.locator('section.bottom').getByRole('tab', { name: 'Cycle View' }).click();
    await expect(page.locator('section.bottom .pbody:visible .notice h3')).toHaveText('아직 사이클이 없습니다');
    await cycles(page, 1);
    await expect(page.locator('.cycleview')).toBeVisible();
    await expect(page.locator('.cycleview')).toHaveClass(/compact/);
    await expect(side(page).getByRole('tab', { name: 'Cycles' })).toBeVisible();
    await expect(side(page).getByRole('tab', { name: 'Cycles' })).toHaveClass(/on/, { timeout: 1000 });
    await expect(page.locator('.ctable tr.hcycle th[data-cycle]')).toHaveText(['0', '1']);
    await expect(page.locator('.ctablebox .cempty')).toContainText('Add to Cycle View');
    await expect(side(page).locator('.regs')).toBeHidden();
    await side(page).getByRole('tab', { name: 'Registers' }).click();
    await expect(side(page).locator('.chint')).toContainText('Register');
  } finally {
    await r.close();
  }
});
