/* Messages (N-13, D-143) with the fake engine, whose diag.* answers are the
   real engine's words for a few broken circuits (tests/fixtures/messages.json):
   the list grouped by kind with a count each, the tab's count, the status
   bar's count (No messages / 1 message / N messages), a message chosen sends
   "show this place" (reveal.ts) and goes to its circuit tab, the list after
   the clock ran (with the cycle; choosing it brings the Cycle View at that
   cycle with the message's place pinned, N-14) and after Reset, the chosen message kept,
   Reset Simulation for an oscillation, the empty panel's word, no character
   on screen while there are messages. */

import { expect, test, type Page } from '@playwright/test';

import { launch, newCircuit, openFile, sample, visibleCharacters } from './harness.ts';

const BROKEN = 'electron/tests/fixtures/broken-datapath.circ';
const OSCILLATION = 'tests/circ/faults/dynamic-oscillation.circ';

// Every "show this place" the page sends, in order.
async function listen(page: Page): Promise<void> {
  await page.evaluate(() => {
    const w = window as unknown as { reveals: unknown[] };
    w.reveals = [];
    window.addEventListener('hcs:reveal', (e) => w.reveals.push((e as CustomEvent).detail));
  });
}
const reveals = (page: Page) => page.evaluate(() => (window as unknown as { reveals: Record<string, unknown>[] }).reveals);

test('a broken circuit: messages grouped by kind with counts, the tab and the status bar count them', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, BROKEN));
    const panel = page.locator('.pbody.bottom').first();
    await expect(panel.locator('.msg')).toHaveCount(2);
    await expect(panel.locator('.msghead .gname')).toHaveText(['Clock not connected', 'Tunnel without a pair']);
    await expect(panel.locator('.msghead .count')).toHaveText(['1', '1']);
    await expect(panel.locator('.msg .say').first()).toHaveText('main › PC (Register) 부품은 클럭 입력 clk 포트가 연결되지 않아 값이 바뀌지 않습니다.');
    await expect(panel.locator('.msg .say').nth(1)).toHaveText('main 회로에는 RegWirte 터널과 이름이 같은 다른 터널이 없습니다. 혹시 RegWrite?');
    await expect(panel.locator('.msg .where')).toHaveText(['main', 'main']);
    await expect(page.getByRole('tab', { name: /Messages/ }).locator('.tabcount')).toHaveText('2');
    await expect(page.locator('.status .msgcount')).toHaveText('2 messages');
    await expect(page.locator('.status .msgcount')).toHaveClass(/err/);
    // A message says the circuit cannot work: no character anywhere (the Canvas's guide too) while there are any.
    await expect(panel.locator('img.char')).toHaveCount(0);
    await expect.poll(() => visibleCharacters(page)).toBe(0);
  } finally {
    await r.close();
  }
});

test('choosing a message: marked, "show this place" with the ids, and its circuit tab on show', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, BROKEN));
    await listen(page);
    // Another circuit on show first: the message takes the Canvas back to main.
    await page.getByRole('tab', { name: 'Circuits' }).click();
    await page.locator('.upper .pbody:visible .list > li', { hasText: 'regfile' }).getByRole('button').click();
    await expect(page.locator('.circuitbar .ptab.on')).toHaveText('regfile');
    const row = page.locator('.msg').nth(1);
    await row.click();
    await expect(row).toHaveClass(/\bon\b/);
    await expect(row).toHaveAttribute('aria-current', 'true');
    await expect(page.locator('.msg.on')).toHaveCount(1);
    await expect(page.locator('.circuitbar .ptab.on')).toHaveText('main');
    // The focus stays on the message (v1 Y-10: it went to the search box), the next one a Tab away.
    await expect(page.locator('.msg').nth(1)).toBeFocused();
    await page.keyboard.press('Shift+Tab');
    await expect(page.locator('.msg').first()).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page.locator('.msg').first()).toHaveClass(/\bon\b/);
    await expect(page.locator('.msg').first()).toBeFocused();
    const sent = await reveals(page);
    expect(sent).toHaveLength(2);
    expect((sent[1] as { messageId: string }).messageId).toBe('d1');
    const rv = sent[0] as { fileId: string; messageId: string; circuitId: string; root: string; path: string[]; components: string[]; wires: string[]; at: number[]; cycle: number | null };
    expect(rv.fileId).toMatch(/^f\d+$/);
    expect(rv.messageId).toBe('d2');
    expect(rv.root).toBe(rv.circuitId);
    expect(rv.path).toEqual([]);
    expect(rv.components).toHaveLength(1);
    expect(rv.components[0]).toMatch(/^k\d+$/);
    expect(rv.at).toEqual([715, 320]);
    expect(rv.cycle).toBeNull();
  } finally {
    await r.close();
  }
});

test('the clock runs: a message the simulation found joins with its cycle; the chosen one stays; Reset takes it away', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, BROKEN));
    await expect(page.locator('.msg')).toHaveCount(2);
    await listen(page);
    await page.locator('.msg').first().click();
    await page.keyboard.press('F10');
    await expect(page.locator('.msg')).toHaveCount(3);
    await expect(page.locator('.status .msgcount')).toHaveText('3 messages');
    await expect(page.locator('.msghead .gname')).toHaveText(['Clock not connected', 'Tunnel without a pair', 'Undefined write input']);
    const dyn = page.locator('.msggroup[data-code="X_WRITE_CONTROL"] .msg');
    await expect(dyn.locator('.say')).toHaveText('사이클 0에 main › DMem #1 부품의 쓰기 입력 MemWrite 포트가 클럭 에지에서 정해지지 않았습니다. 원인: main › MemWrite 입력 핀의 값이 정해지지 않았습니다.');
    await expect(dyn.locator('.where')).toHaveText('main · Cycle 0');
    await expect(page.locator('.msg').first()).toHaveClass(/\bon\b/); // kept by its id
    await dyn.click();
    const sent = await reveals(page);
    expect((sent.at(-1) as { cycle: number }).cycle).toBe(0);
    expect((sent.at(-1) as { components: string[] }).components).toHaveLength(2);
    // a message with a cycle: the Cycle View comes forward at that cycle, its place pinned on top (v1 D-05, V-03, D-144)
    await expect(page.getByRole('tab', { name: 'Cycle View' })).toHaveAttribute('aria-selected', 'true');
    const temp = page.locator('.ctable tr.crow.temp');
    await expect(temp).toHaveCount(1);
    await expect(temp.locator('.rname')).toHaveText('MemWrite');
    await expect(temp.locator('td.pin')).toHaveAttribute('data-cycle', '0');
    await expect(page.locator('.ctable tr.crow').first()).toHaveClass(/\btemp\b/);
    await page.getByRole('button', { name: /Reset/ }).first().click();
    await expect(page.locator('.msg')).toHaveCount(2);
    await expect(page.locator('.status .msgcount')).toHaveText('2 messages');
    await expect(temp).toHaveCount(0); // the message went, and its rows with it (D-114)
  } finally {
    await r.close();
  }
});

test('an oscillation: its loop replaces the static loop, Reset Simulation brings the simulation back', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await openFile(r, sample(r.dir, OSCILLATION));
    await expect(page.locator('.msghead .gname')).toHaveText(['Combinational loop']);
    await expect(page.locator('.msgactions')).toHaveCount(0);
    await page.keyboard.press('F10');
    await expect(page.locator('.msghead .gname')).toHaveText(['Oscillation']);
    await expect(page.locator('.msg .say')).toContainText('(발진)');
    await expect(page.locator('.status')).toContainText('발진으로 시뮬레이션이 꺼졌습니다');
    await page.getByRole('button', { name: 'Reset Simulation' }).click();
    await expect(page.locator('.msghead .gname')).toHaveText(['Combinational loop']);
    await expect(page.locator('.status')).not.toContainText('꺼졌습니다');
    await expect(page.locator('.status .msgcount')).toHaveText('1 message');
  } finally {
    await r.close();
  }
});

test('nothing to say: the empty panel says what fills it, the status bar "No messages"; its count opens the tab; the list follows the file on show', async () => {
  const r = await launch();
  const { page } = r;
  try {
    await newCircuit(r);
    const panel = page.locator('.pbody.bottom').first();
    await expect(panel.locator('.notice h3')).toHaveText('메시지가 없습니다');
    await expect(panel.locator('.notice p')).toContainText('시뮬레이션 중에 생긴 E·X 값과 발진은 그 사이클과 함께 나옵니다.');
    await expect(page.locator('.status .msgcount')).toHaveText('No messages');
    await expect(page.locator('.status .msgcount')).not.toHaveClass(/err/);
    await expect(page.getByRole('tab', { name: /Messages/ }).locator('.tabcount')).toHaveCount(0);
    await expect.poll(() => visibleCharacters(page)).toBe(1); // the empty Canvas's guide
    await openFile(r, sample(r.dir, BROKEN));
    await expect(page.locator('.status .msgcount')).toHaveText('2 messages');
    await expect.poll(() => visibleCharacters(page)).toBe(0);
    await page.locator('.filebar .ptab', { hasText: 'untitled.circ' }).click();
    await expect(page.locator('.status .msgcount')).toHaveText('No messages');
    await expect.poll(() => visibleCharacters(page)).toBe(1);
    await expect(panel.locator('.notice h3')).toHaveText('메시지가 없습니다');
    // The count opens the Messages tab (from another tab, and from a folded panel).
    await page.locator('.filebar .ptab', { hasText: 'broken-datapath.circ' }).click();
    await page.locator('section.bottom').getByRole('tab', { name: 'Console' }).click();
    await page.locator('section.bottom').getByRole('button', { name: 'Collapse' }).click();
    await page.locator('.status .msgcount').click();
    await expect(page.locator('.msg').first()).toBeVisible();
    await expect(page.getByRole('tab', { name: /Messages/ })).toHaveAttribute('aria-selected', 'true');
  } finally {
    await r.close();
  }
});
