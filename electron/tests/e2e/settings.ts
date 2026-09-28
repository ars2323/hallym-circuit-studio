/* Every setting the window has now (src/renderer/app/logic/run-settings.ts,
   N-19, D-152), for the lab-PC tests: what each is, read from the screen,
   and a change of each the way a student makes it.  A new setting goes
   here too; labpc.e2e.ts (and real-engine.e2e.ts) check that none of them
   outlives the run. */

import { expect, type Page } from '@playwright/test';

export interface Settings {
  zoom: string;                 // the Canvas's zoom (the status bar)
  hz: string;                   // the clock speed
  busWidths: boolean;           // Wire Colors › Show Bus Widths
  sizes: Record<string, string>;   // the splitters' places (the panels' sizes)
  bottomCollapsed: boolean;
  tabs: string[];               // the tab on show in each panel
  circuitTabs: string[];        // the circuits opened as tabs
}

export function settingsNow(page: Page): Promise<Settings> {
  return page.evaluate(() => {
    const shell = document.querySelector<HTMLElement>('.shell')!;
    const box = document.querySelector<HTMLInputElement>('.legend-panel input[type=checkbox]')!;
    const on = (sel: string) => document.querySelector(`${sel} .ptab.on`)?.textContent ?? '';
    return {
      zoom: document.querySelector('.zoom-button')?.textContent ?? '',
      hz: document.querySelector<HTMLSelectElement>('select[aria-label="Clock speed"]')!.value,
      busWidths: box.checked,
      sizes: Object.fromEntries(['--left-w', '--right-w', '--bottom-h', '--lower-h'].map((v) => [v, shell.style.getPropertyValue(v)])),
      bottomCollapsed: document.querySelector('.panel.bottom')!.classList.contains('collapsed'),
      tabs: [on('.panel.upper'), on('.panel.lower'), on('.panel.bottom')],
      circuitTabs: [...document.querySelectorAll('.circuitbar .ptab')].map((t) => t.textContent ?? ''),
    };
  });
}

// Drags a splitter by (dx, dy): .splitter between columns, .vgrip between rows (shared/splitter.ts).
async function drag(page: Page, selector: string, dx: number, dy: number): Promise<void> {
  const s = (await page.locator(selector).boundingBox())!;
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
  await page.locator('select[aria-label="Clock speed"]').selectOption({ label: '64 Hz' });
  await page.locator('.legend-button').click();
  const panel = page.locator('.legend-panel');
  await expect(panel).toBeVisible();
  await expect(panel.locator('.legend-note')).toHaveText('이번 실행에만 적용됩니다');
  await panel.locator('input[type=checkbox]').uncheck();
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
}
