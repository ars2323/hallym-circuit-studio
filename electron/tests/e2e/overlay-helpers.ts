/* The overlays' test helpers (N-15): what the overlays show (controller.ts shown()), a circuit point on the
   page, a part's middle, a wire at a port, clicks and the right-click menu, a colour near a point. */

import { expect, type Page } from '@playwright/test';

import { DATAPATH, openFile, type Running, sample } from './harness.ts';
import { decodePng } from './png.ts';

export type Shown = {
  flow: { running: boolean; backward: boolean; ends: string[]; lit: number; labels: string[]; onClick: boolean; t: number; total: number; jumps: number;
    drawn: { arcs: { from: [number, number]; to: [number, number]; control: [number, number]; color: string; whole: boolean }[]; labels: { text: string; box: { x0: number; y0: number; x1: number; y1: number } }[] } };
  influence: { mode: string; depth: number; maxDepth: number; forward: { wires: string[] }; backward: { wires: string[] }; origin: string[] } | null;
  activePath: number; fields: string[]; groups: boolean; highlight: number;
  bus: { net: string; text: string; box: { x0: number; y0: number; x1: number; y1: number } }[];
  memos: { x: number; y: number; w: number; h: number; color: number; text: string }[];
};
export const shown = (page: Page): Promise<Shown> => page.evaluate(() => (window as unknown as { __hcsOverlays: { shown(): unknown } }).__hcsOverlays.shown() as never);

export async function opened(r: Running, cycles = 1): Promise<void> {
  await openFile(r, sample(r.dir, DATAPATH));
  await r.page.waitForFunction(() => {
    const c = (window as unknown as { __hcsCanvas?: { scene: { values: Map<string, string> } | null } }).__hcsCanvas;
    return !!c?.scene && c.scene.values.size > 0;
  });
  for (let k = 1; k <= cycles; k++) {
    await r.page.getByRole('button', { name: /1 Cycle/ }).click();
    await r.page.locator('.status', { hasText: `Cycle ${k}` }).waitFor();
  }
  await setView(r.page, { x: 70, y: 40, zoom: 1 });
}

export async function setView(page: Page, v: { x: number; y: number; zoom: number }): Promise<void> {
  await page.evaluate((w) => (window as unknown as { __hcsCanvas: { setView(v: object): void } }).__hcsCanvas.setView(w), v);
  await page.waitForTimeout(150);
}

// A circuit point on the page (CSS px).
export const onPage = (page: Page, p: [number, number]) => page.evaluate((q) => {
  const c = (window as unknown as { __hcsCanvas: { canvas: HTMLCanvasElement; view: { x: number; y: number; zoom: number } } }).__hcsCanvas;
  const r = c.canvas.getBoundingClientRect();
  return { x: r.left + (q[0] - c.view.x) * c.view.zoom, y: r.top + (q[1] - c.view.y) * c.view.zoom };
}, p);

// A part's middle (by name and label) and a wire (by a predicate on its ends) in circuit units.
export const partMiddle = (page: Page, name: string, label?: string) => page.evaluate(([n, l]) => {
  const c = (window as unknown as { __hcsCanvas: { scene: { components: Map<string, { id: string; name: string; attrs: Record<string, string>; bounds: number[] }> } } }).__hcsCanvas;
  const k = [...c.scene.components.values()].find((x) => x.name === n && (l === undefined || x.attrs.label === l))!;
  return { id: k.id, at: [k.bounds[0] + k.bounds[2] / 2, k.bounds[1] + k.bounds[3] / 2] as [number, number] };
}, [name, label] as const);

// The longest wire of the net at a component's port (its middle point, the wire's and net's ids).
export const wireAtPort = (page: Page, name: string, portName: string) => page.evaluate(([n, pn]) => {
  type C = { id: string; name: string; ports: { i: number; name?: string }[] };
  const c = (window as unknown as { __hcsCanvas: { scene: { components: Map<string, C>; wires: Map<string, { id: string; a: number[]; b: number[] }>; netOf(id: string, i: number): { id: string; wires: string[] } | undefined } } }).__hcsCanvas;
  const k = [...c.scene.components.values()].find((x) => x.name === n)!;
  const net = c.scene.netOf(k.id, k.ports.find((q) => q.name === pn)!.i)!;
  const len = (w: { a: number[]; b: number[] }) => Math.abs(w.a[0] - w.b[0]) + Math.abs(w.a[1] - w.b[1]);
  const w = net.wires.map((id) => c.scene.wires.get(id)!).sort((a, b) => len(b) - len(a))[0];
  return { wire: w.id, net: net.id, at: [(w.a[0] + w.b[0]) / 2, (w.a[1] + w.b[1]) / 2] as [number, number], horizontal: w.a[1] === w.b[1],
    quarter: [(3 * w.a[0] + w.b[0]) / 4, (3 * w.a[1] + w.b[1]) / 4] as [number, number] };
}, [name, portName] as const);

export async function click(page: Page, p: [number, number], shift = false): Promise<void> {
  const q = await onPage(page, p);
  if (shift) await page.keyboard.down('Shift');
  await page.mouse.click(q.x, q.y);
  if (shift) await page.keyboard.up('Shift');
}

export async function rightClick(page: Page, p: [number, number]): Promise<void> {
  const q = await onPage(page, p);
  await page.mouse.click(q.x, q.y, { button: 'right' });
  await page.locator('.ovmenu').first().waitFor();
}

// Down the menu's items by their names (a submenu opens on its item).
export async function menu(page: Page, ...path: string[]): Promise<void> {
  for (const [i, label] of path.entries()) {
    const items = page.locator('.ovmenu').nth(i).locator('button');
    const n = await items.count();
    let hit = -1;
    for (let k = 0; k < n && hit < 0; k++) if ((await items.nth(k).locator('.label').innerText()).trim() === label) hit = k;
    expect(hit, `menu item ${label}`).toBeGreaterThanOrEqual(0);
    await items.nth(hit).click();
  }
}

// Whether a pixel close to `color` (within `tol` per channel) is in the square around a page point.
export async function colourNear(page: Page, at: { x: number; y: number }, color: string, radius = 6, tol = 40): Promise<boolean> {
  const x = Math.round(at.x - radius), y = Math.round(at.y - radius), n = radius * 2 + 1;
  const png = decodePng(await page.screenshot({ clip: { x, y, width: n, height: n } }));
  const want = [1, 3, 5].map((k) => parseInt(color.slice(k, k + 2), 16));
  for (let i = 0; i < png.rgba.length; i += 4) {
    if ([0, 1, 2].every((k) => Math.abs(png.rgba[i + k] - want[k]) <= tol)) return true;
  }
  return false;
}

