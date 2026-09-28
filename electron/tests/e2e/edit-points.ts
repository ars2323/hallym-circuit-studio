/* Editing on the Canvas from the tests (N-08): a real mouse press, moves
   and release at circuit points; the scene's parts and wires, the engine's
   selection and the tool's overlay read back. */

import type { Page } from '@playwright/test';

import { pagePoint } from './canvas-points.ts';

type P = [number, number];
interface Part { id: string; lib: string | null; name: string; loc: P; bounds: number[]; facing: string | null; attrs: Record<string, string>; ports: { i: number; loc: P; width: number; dir: string }[] }
interface W { id: string; a: P; b: P }
type C = {
  scene: { fileId: string; circuitId: string; components: Map<string, Part>; wires: Map<string, W>; netOf(id: string, i: number): { id: string } | undefined; wireNet(id: string): { id: string } | undefined } | null;
  overlay: Record<string, unknown>;
  selection(): string[];
  view: { x: number; y: number; zoom: number };
};

// A drag at circuit points: press at `from`, through `via` (each in `steps` moves), release at the last.
export async function drag(page: Page, from: P, via: P[], options: { steps?: number; modifiers?: ('Shift' | 'Alt' | 'Control')[] } = {}): Promise<void> {
  const a = await pagePoint(page, from);
  for (const m of options.modifiers ?? []) await page.keyboard.down(m);
  await page.mouse.move(a.x, a.y);
  await page.mouse.down();
  for (const p of via) {
    const q = await pagePoint(page, p);
    await page.mouse.move(q.x, q.y, { steps: options.steps ?? 6 });
  }
  await page.mouse.up();
  for (const m of options.modifiers ?? []) await page.keyboard.up(m);
}

export async function hover(page: Page, p: P): Promise<void> {
  const q = await pagePoint(page, p);
  await page.mouse.move(q.x, q.y, { steps: 3 });
}

export const where = (page: Page): Promise<{ fileId: string; circuitId: string }> => page.evaluate(() => {
  const s = (window as unknown as { __hcsCanvas: C }).__hcsCanvas.scene!;
  return { fileId: s.fileId, circuitId: s.circuitId };
});

export const parts = (page: Page, name?: string): Promise<Part[]> => page.evaluate((n) =>
  [...((window as unknown as { __hcsCanvas: C }).__hcsCanvas.scene?.components.values() ?? [])].filter((k) => !n || k.name === n), name);

export const wires = (page: Page): Promise<W[]> => page.evaluate(() =>
  [...((window as unknown as { __hcsCanvas: C }).__hcsCanvas.scene?.wires.values() ?? [])].map((w) => ({ id: w.id, a: w.a, b: w.b })));

// Whether two ports are on one net (the engine's nets in the scene).
export const sameNet = (page: Page, a: [string, number], b: [string, number]): Promise<boolean> => page.evaluate(([x, y]) => {
  const s = (window as unknown as { __hcsCanvas: C }).__hcsCanvas.scene!;
  const n = s.netOf(x[0], x[1]), m = s.netOf(y[0], y[1]);
  return !!n && !!m && n.id === m.id;
}, [a, b] as const);

export const selected = (page: Page): Promise<string[]> => page.evaluate(() => (window as unknown as { __hcsCanvas: C }).__hcsCanvas.selection().sort());

export const overlay = (page: Page): Promise<Record<string, unknown>> => page.evaluate(() =>
  JSON.parse(JSON.stringify((window as unknown as { __hcsCanvas: C }).__hcsCanvas.overlay, (_k, v) => (v instanceof Set ? [...v] : v))));

// The wires as segments sorted, for comparing shapes ("x0,y0-x1,y1" with the smaller end first).
export const segments = (ws: W[]): string[] => ws.map((w) => {
  const [a, b] = w.a[0] < w.b[0] || (w.a[0] === w.b[0] && w.a[1] <= w.b[1]) ? [w.a, w.b] : [w.b, w.a];
  return `${a[0]},${a[1]}-${b[0]},${b[1]}`;
}).sort();

// hcs:place-tool as the Components list sends it (N-12's contract); whether a listener took it.
export const placeTool = (page: Page, detail: Record<string, unknown>): Promise<boolean> => page.evaluate((d) =>
  !window.dispatchEvent(new CustomEvent('hcs:place-tool', { detail: d, cancelable: true })), detail);

export const tool = (page: Page, name: string) => page.getByRole('radio', { name, exact: true });

