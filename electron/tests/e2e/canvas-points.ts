/* Circuit points on the page, for the tests that press on the Canvas
   (N-07 Poke, N-08 editing): the Canvas (canvas.ts puts itself on window)
   set to a known view, a circuit point turned into a page point, the scene's
   parts and values read back. */

import type { Page } from '@playwright/test';

type C = {
  canvas: HTMLCanvasElement;
  view: { x: number; y: number; zoom: number };
  setView(v: { x: number; y: number; zoom: number }): void;
  scene: { circuitId: string; components: Map<string, { id: string; name: string; attrs: Record<string, string>; bounds: number[]; loc: number[]; ports: { i: number; loc: number[] }[]; subcircuit?: string }>;
    portValue(id: string, i: number): string | undefined; wires: Map<string, { id: string; a: number[]; b: number[] }> } | null;
  overlay: Record<string, unknown>;
  selection(): string[];
};

// The view: circuit point (x, y) at the Canvas's top-left, at `zoom` (no animation).
export async function setView(page: Page, x: number, y: number, zoom = 1): Promise<void> {
  await page.evaluate((v) => (window as unknown as { __hcsCanvas: C }).__hcsCanvas.setView(v), { x, y, zoom });
  await page.waitForTimeout(50);
}

// The view with circuit point p in the middle of the Canvas, at `zoom`.
export async function centerOn(page: Page, p: [number, number], zoom = 1): Promise<void> {
  await page.evaluate(([pt, z]) => {
    const c = (window as unknown as { __hcsCanvas: C }).__hcsCanvas;
    const r = c.canvas.getBoundingClientRect();
    c.setView({ x: pt[0] - r.width / 2 / z, y: pt[1] - r.height / 2 / z, zoom: z });
  }, [p, zoom] as const);
  await page.waitForTimeout(50);
}

// A circuit point as a page point (CSS px).
export async function pagePoint(page: Page, p: [number, number]): Promise<{ x: number; y: number }> {
  return page.evaluate((pt) => {
    const c = (window as unknown as { __hcsCanvas: C }).__hcsCanvas;
    const r = c.canvas.getBoundingClientRect();
    return { x: r.left + (pt[0] - c.view.x) * c.view.zoom, y: r.top + (pt[1] - c.view.y) * c.view.zoom };
  }, p);
}

// A part of the scene by its label and name (either may be '' for any).
export async function partBy(page: Page, label: string, name?: string): Promise<{ id: string; name: string; bounds: number[]; loc: number[]; ports: { i: number; loc: number[] }[] } | null> {
  return page.evaluate(([l, n]) => {
    const c = (window as unknown as { __hcsCanvas: C }).__hcsCanvas;
    for (const k of c.scene?.components.values() ?? []) {
      if ((!l || k.attrs.label === l) && (!n || k.name === n)) return { id: k.id, name: k.name, bounds: k.bounds, loc: k.loc, ports: k.ports };
    }
    return null;
  }, [label, name ?? ''] as const);
}

// The value on a part's port now (the net's, as the Canvas has it).
export const portValue = (page: Page, id: string, i = 0): Promise<string | null> => page.evaluate(([k, p]) =>
  (window as unknown as { __hcsCanvas: C }).__hcsCanvas.scene?.portValue(k, p) ?? null, [id, i] as const);

export const overlayOf = (page: Page): Promise<Record<string, unknown>> => page.evaluate(() =>
  JSON.parse(JSON.stringify((window as unknown as { __hcsCanvas: C }).__hcsCanvas.overlay, (_k, v) => (v instanceof Set ? [...v] : v))));

export const selectionOf = (page: Page): Promise<string[]> => page.evaluate(() => (window as unknown as { __hcsCanvas: C }).__hcsCanvas.selection().sort());

// Clicks at a circuit point (a real mouse press and release on the Canvas).
export async function clickAt(page: Page, p: [number, number], options: { modifiers?: ('Shift' | 'Control' | 'Alt')[]; count?: number } = {}): Promise<void> {
  const q = await pagePoint(page, p);
  for (const m of options.modifiers ?? []) await page.keyboard.down(m);
  await page.mouse.click(q.x, q.y, { clickCount: options.count ?? 1 });
  for (const m of options.modifiers ?? []) await page.keyboard.up(m);
}
