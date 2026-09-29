/* The title bar's icons as Hallym MIPS v2.6.0 has them (A-07 part 2,
   D-169): Menu, then Tutorial, New, Open, Export Image, Preferences at the
   right end, in its places and shapes; the export only with a circuit on
   show and an exporter (N-21's); About in Preferences.  On the first screen
   the bars are dark glass over the photo, the icons white. */

import { expect, test } from '@playwright/test';

import { launch, newCircuit } from './harness.ts';

const names = (r: { page: import('@playwright/test').Page }) =>
  r.page.locator('.titlebar .tools .iconbtn:visible').evaluateAll((els) => els.map((e) => (e as HTMLElement).title));
const icons = (r: { page: import('@playwright/test').Page }) =>
  r.page.locator('.titlebar .tools .iconbtn:visible img').evaluateAll((els) => els.map((e) => (e as HTMLImageElement).src.split('/').pop()));

test('the icons: Menu, Tutorial, New, Open, Preferences (Hallym MIPS\'s order and shapes); Export Image waits for its exporter; white on the first screen\'s glass, dark on the white bar', async () => {
  const r = await launch();
  const { page } = r;
  try {
    expect(await names(r)).toEqual(['Menu', 'Tutorial', 'New circuit (Ctrl+N)', 'Open file (Ctrl+O)', 'Preferences']);
    expect(await icons(r)).toEqual(['menu.svg', 'circle-question-mark.svg', 'file-plus.svg', 'folder-open.svg', 'settings.svg']);
    await expect(page.locator('body')).toHaveClass(/first-screen/);
    await expect(page.locator('.titlebar .iconbtn img').first()).toHaveCSS('filter', 'brightness(0) invert(1)');
    // the export is in its place, not shown until N-21's exporter is there
    await expect(page.getByTitle('Export Image…')).toBeHidden();
    await expect(page.getByTitle('Export Image…').locator('img')).toHaveAttribute('src', /image-down\.svg$/);
    // Tutorial with no course yet: the card asks the course (step 1)
    await page.getByTitle('Tutorial').click();
    await expect(page.locator('.action').first()).toContainText('논리설계 및 실험');
    await newCircuit(r, '논리설계 및 실험');
    await expect(page.locator('body')).not.toHaveClass(/first-screen/);
    await expect(page.locator('.titlebar')).toHaveCSS('background-color', 'rgb(255, 255, 255)');
    await expect(page.locator('.titlebar .iconbtn img').first()).toHaveCSS('filter', 'none');
    expect(await names(r)).toEqual(['Menu', 'Tutorial', 'New circuit (Ctrl+N)', 'Open file (Ctrl+O)', 'Preferences']);
    // Tutorial with a course: that course's (N-18's; until then a new circuit in it)
    await page.getByTitle('Tutorial').click();
    await expect(page.locator('.filebar .ptab')).toHaveCount(2);
    await expect(page.locator('.titlebar .coursechip')).toHaveText('논리설계 및 실험');
    // About: in Preferences, as Hallym MIPS has it
    await page.getByTitle('Preferences').click();
    await expect(page.locator('dialog.prefs').getByRole('button', { name: 'About · Licenses' })).toBeVisible();
  } finally {
    await r.close();
  }
});
