/* The engine's model as the window can ask for it (window.app.call), for
   the recovery tests: the calls go through the main process as the
   window's own, so its journal records them (src/main/recovery.ts).
   A file's model is compared without the engine's ids: a new engine gives
   its parts new ones (docs/engine-api.md 3, 7). */

import type { ElectronApplication, Page } from '@playwright/test';

import type { LibraryGroup, Snapshot } from '../../src/main/protocol.ts';

type AppWindow = { app: { call(method: string, params: unknown): Promise<unknown> } };

export async function call<T = unknown>(page: Page, method: string, params: Record<string, unknown> = {}): Promise<T> {
  return page.evaluate(([m, p]) => (window as unknown as AppWindow).app.call(m, p), [method, params] as const) as Promise<T>;
}

// The files the main process has open, in the order they were opened: their ids.
export const openFileIds = (app: ElectronApplication): Promise<string[]> => app.evaluate(() =>
  [...(globalThis as unknown as { __hcs: { recovery: { journal: { files: Map<string, unknown> } } } }).__hcs.recovery.journal.files.keys()]);

// The unsaved edits the journal holds for a file.
export const journalLength = (app: ElectronApplication, fileId: string): Promise<number> => app.evaluate((_e, id) =>
  (globalThis as unknown as { __hcs: { recovery: { journal: { unsaved(id: string): number } } } }).__hcs.recovery.journal.unsaved(id), fileId);

// Ends the engine as a crash would; its process id before.
export const killEngine = (app: ElectronApplication): Promise<number> => app.evaluate(() => {
  const e = (globalThis as unknown as { __hcs: { engine: { pid: number; kill(): void } } }).__hcs.engine;
  const pid = e.pid;
  e.kill();
  return pid;
});
export const enginePid = (app: ElectronApplication): Promise<number | undefined> => app.evaluate(() =>
  (globalThis as unknown as { __hcs: { engine: { pid?: number } } }).__hcs.engine.pid);

// {circuit name: circuitId} of a file (Components' first group: this file's circuits).
export async function circuitsOf(page: Page, fileId: string): Promise<Record<string, string>> {
  const lib = await call<LibraryGroup[]>(page, 'model.library', { fileId });
  return Object.fromEntries((lib.find((g) => g.lib === null)?.tools ?? []).map((t) => [t.name, t.circuitId!]));
}

const pt = (p: number[]) => `${p[0]},${p[1]}`;

// A circuit without ids: parts, wires, junctions, and nets as the parts they join.
export function normal(s: Snapshot): unknown {
  const keyOf = new Map<string, string>();
  const comps = s.components.map((c) => {
    const attrs = Object.keys(c.attrs).sort().map((k) => [k, c.attrs[k]]);
    const k = JSON.stringify([c.lib, c.name, c.loc, c.bounds, c.facing, attrs, c.ports.map((p) => [p.i, p.loc, p.width, p.dir, p.name ?? null]), c.subcircuit ?? null]);
    keyOf.set(c.id, k);
    return k;
  });
  const wires = s.wires.map((w) => {
    const k = [pt(w.a), pt(w.b)].sort().join('-');
    keyOf.set(w.id, k);
    return k;
  });
  const nets = s.nets.map((n) => JSON.stringify([n.width, n.wires.map((w) => keyOf.get(w)).sort(), n.ports.map(([c, i]) => `${keyOf.get(c)}#${i}`).sort()]));
  return { name: s.name, comps: comps.sort(), wires: wires.sort(), junctions: s.junctions.map(pt).sort(), nets: nets.sort() };
}

// Every circuit of a file, without ids, by name.
export async function fileModel(page: Page, fileId: string): Promise<Record<string, unknown>> {
  const out: Record<string, unknown> = {};
  for (const [name, circuitId] of Object.entries(await circuitsOf(page, fileId))) {
    out[name] = normal(await call<Snapshot>(page, 'model.circuit', { fileId, circuitId }));
  }
  return out;
}

// Kills the Electron main process itself (its own pid, from inside) and waits for it and then
// for the engine to be gone: the engine must not outlive it.  What is still alive, in the failure.
export async function killMainAndSeeEngineEnd(app: ElectronApplication, enginePid: number, timeoutMs: number): Promise<string> {
  const main = await app.evaluate(() => process.pid);
  process.kill(main, 'SIGKILL');
  const t0 = Date.now();
  while (Date.now() - t0 < timeoutMs) {
    if (!alive(main) && !alive(enginePid)) return 'both ended';
    await new Promise((done) => setTimeout(done, 100));
  }
  return `after ${timeoutMs} ms: main ${main} ${alive(main) ? 'alive' : 'ended'}, engine ${enginePid} ${alive(enginePid) ? 'alive' : 'ended'}`;
}

export const alive = (pid: number): boolean => {
  try { process.kill(pid, 0); return true; } catch (e) { return (e as NodeJS.ErrnoException).code === 'EPERM'; }
};
