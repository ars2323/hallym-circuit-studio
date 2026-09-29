/* Every setting the window has now (src/renderer/app/logic/run-settings.ts,
   N-19, D-152), for the lab-PC tests: what each is, read from the screen,
   and a change of each the way a student makes it.  A new setting goes
   here too; labpc.e2e.ts (and real-engine.e2e.ts) check that none of them
   outlives the run. */

import { expect, type Page } from '@playwright/test';

import { clockSpeed } from './harness.ts';

export interface Settings {
  zoom: string;                 // the Canvas's zoom (the status bar)
  hz: string;                   // the clock speed
  busWidths: boolean;           // Wire Colors › Show Bus Widths
  sizes: Record<string, string>;   // the splitters' places (the panels' sizes)
  bottomCollapsed: boolean;
  tabs: string[];               // the tab on show in each panel
  circuitTabs: string[];        // the circuits opened as tabs
  // the overlays' (N-15): Wire Colors › Colors, Bus Values, Active Path; the Signal Flow menu's settings
  colors: string;
  busValues: string;
  activePath: boolean;
  flow: Record<string, unknown>;
  keys: string;                 // Preferences › Keyboard (D-158): a changed key shows in its command's tooltip
  grid: boolean;                // the zoom menu's Show Grid (v1 I-119, D-158)
}

export function settingsNow(page: Page): Promise<Settings> {
  return page.evaluate(() => {
    const shell = document.querySelector<HTMLElement>('.shell')!;
    const box = document.querySelector<HTMLInputElement>('.legend-panel > label.legend-opt:not(.ovrow) input')!;
    const ov = (window as unknown as { __hcsOverlays: { settings: Record<string, unknown> } }).__hcsOverlays;
    const on = (sel: string) => document.querySelector(`${sel} .ptab.on`)?.textContent ?? '';
    return {
      zoom: document.querySelector('.zoom-button')?.textContent ?? '',
      hz: document.querySelector<HTMLSelectElement>('select[aria-label="Clock speed"]')!.value,
      busWidths: box.checked,
      sizes: Object.fromEntries(['--left-w', '--right-w', '--bottom-h', '--lower-h'].map((v) => [v, shell.style.getPropertyValue(v)])),
      bottomCollapsed: document.querySelector('.panel.bottom')!.classList.contains('collapsed'),
      tabs: [on('.panel.upper'), on('.panel.lower'), on('.panel.bottom')],
      circuitTabs: [...document.querySelectorAll('.circuitbar .ptab')].map((t) => t.textContent ?? ''),
      colors: document.querySelector('.legend-panel .ovseg [aria-pressed="true"]')?.textContent ?? '',
      busValues: document.querySelector<HTMLSelectElement>('.legend-panel select[aria-label="Bus Values"]')?.value ?? '',
      activePath: document.querySelector<HTMLInputElement>('.legend-panel .ovlegend label.ovrow.legend-opt input')?.checked ?? false,
      flow: { ...ov.settings },
      keys: document.querySelector<HTMLElement>('.toolbar .flowtoggle')?.title ?? '',
      grid: (window as unknown as { __hcsCanvas: { showGrid: boolean } }).__hcsCanvas.showGrid,
    };
  });
}

// Drags a splitter by (dx, dy): .splitter between columns, .vgrip between rows (shared/splitter.ts).
async function drag(page: Page, selector: string, dx: number, dy: number): Promise<void> {
  const found = await page.locator(selector).boundingBox();
  if (!found) return;   // not in this layout (a narrow window has no right column)
  const s = found;
  await page.mouse.move(s.x + s.width / 2, s.y + s.height / 2);
  await page.mouse.down();
  await page.mouse.move(s.x + s.width / 2 + dx, s.y + s.height / 2 + dy, { steps: 4 });
  await page.mouse.up();
}

// Changes every setting (a file with a circuit named `other` is open, its Canvas drawn).
export async function changeEverySetting(page: Page, other: string): Promise<void> {
  await expect(page.locator('.zoom-button')).toBeVisible();
  const zoom = await page.locator('.zoom-button').textContent();
  await page.keyboard.press('Control+=');
  await page.keyboard.press('Control+=');
  await expect(page.locator('.zoom-button')).not.toHaveText(zoom ?? '');
  await clockSpeed(page, '64 Hz');
  await page.locator('.zoom-button').click();
  await page.locator('.zoom-menu').getByRole('menuitemcheckbox', { name: 'Show Grid' }).click();
  await page.locator('.legend-button').click();
  const panel = page.locator('.legend-panel');
  await expect(panel).toBeVisible();
  await expect(panel.locator('.legend-note')).toHaveText('이번 실행에만 적용됩니다');
  await panel.locator('> label.legend-opt:not(.ovrow) input').uncheck();
  await panel.getByRole('button', { name: 'Groups' }).click();
  await panel.locator('select[aria-label="Bus Values"]').selectOption({ index: 1 });
  const active = panel.locator('.ovlegend label.ovrow.legend-opt input');
  await active.setChecked(!(await active.isChecked()));
  await page.keyboard.press('Escape');
  await expect(panel).toBeHidden();
  await drag(page, '.splitter >> nth=0', 80, 0);     // left | center
  await drag(page, '.splitter >> nth=1', -50, 0);    // center | Attributes
  await drag(page, '.vgrip >> nth=0', 0, -60);       // Components | Tunnels
  await page.getByRole('tab', { name: 'Circuits' }).click();
  await page.locator('.upper .pbody:visible .list > li', { hasText: other }).getByRole('button').click();
  await page.getByRole('tab', { name: 'Minimap' }).click();
  await page.getByRole('tab', { name: 'Console' }).click();
  await page.getByRole('button', { name: 'Collapse' }).click();
  // The Signal Flow menu's settings (N-15; its menu is tested in overlays.e2e.ts): each the other way
  await page.evaluate(() => {
    const o = (window as unknown as { __hcsOverlays: { settings: Record<string, unknown>; toggleOnClick(): void; setFlow(k: string, v: unknown): void } }).__hcsOverlays;
    o.toggleOnClick();
    o.setFlow('speed', 'fast');
    for (const k of ['throughRegisters', 'activePathOnly', 'reduceMotion', 'smooth']) o.setFlow(k, !o.settings[k]);
  });
  // A key (Preferences › Keyboard, D-158): Signal Flow on Click given Ctrl+Shift+G
  await page.getByTitle('Preferences').click();
  const prefs = page.locator('dialog.prefs');
  await expect(prefs.locator('.prefnote')).toContainText('이번 실행에만 적용됩니다');
  await prefs.getByRole('tab', { name: 'Keyboard' }).click();
  await prefs.getByRole('button', { name: 'Change Signal Flow on Click' }).click();
  await page.keyboard.press('Control+Shift+G');
  await expect(prefs.locator('tr[data-command="flowToggle"] kbd')).toHaveText('Ctrl+Shift+G');
  await page.keyboard.press('Escape');
  await expect(prefs).toBeHidden();
}
