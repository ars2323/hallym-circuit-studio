/* The app's windows (N-11, D-153; v1 P-06 Detach Tab / Attach Tab / View
   Side by Side, docs/interaction-parity.md I-179, I-180, I-156, I-182).

   One main window holds the file tabs.  A file tab can be taken out into a
   window of its own (Detach Tab, or dragged out of the window) and put back
   (Attach Tab); View Side by Side takes it out and puts the two windows
   side by side, each half of the screen.  A window of its own holds that
   one file: closing it (its close button, Ctrl+W) closes the file -- after
   the save question -- and the window.  Nothing of this is kept for the
   next run (the lab-PC rule: every start is the first screen, maximised).

   The engine is one for all windows.  Its notifications go to the window
   that holds the file they name (params.fileId), the others (engine.log)
   to the main window; the engine's status and a recovery go to every
   window.  The file's place on screen is the only state a window gets from
   another: its name, path, circuit tabs and the circuit on show. */

import type { BrowserWindow } from 'electron';

// What a window of its own starts from: the file and how its tabs stood.
export interface Handover {
  fileId: string;
  name: string;
  path: string | null;
  tabs: string[];
  circuit: string;
}

export class FileWindows {
  private readonly own = new Map<string, BrowserWindow>();      // fileId → its own window
  private readonly handovers = new Map<number, Handover>();     // a window being made → what it starts from

  private readonly mainWindow: () => BrowserWindow | null;

  constructor(mainWindow: () => BrowserWindow | null) { this.mainWindow = mainWindow; }

  // The window a file is in (its own, else the main window).
  ownerOf(fileId: string | null | undefined): BrowserWindow | null {
    const w = fileId ? this.own.get(fileId) : undefined;
    return w && !w.isDestroyed() ? w : this.mainWindow();
  }

  // Every window, the main one first.
  all(): BrowserWindow[] {
    const m = this.mainWindow();
    return [...(m && !m.isDestroyed() ? [m] : []), ...[...this.own.values()].filter((w) => !w.isDestroyed())];
  }

  // The windows of their own (not the main one).
  detached(): { fileId: string; window: BrowserWindow }[] {
    return [...this.own.entries()].filter(([, w]) => !w.isDestroyed()).map(([fileId, window]) => ({ fileId, window }));
  }

  fileOf(w: BrowserWindow): string | null {
    for (const [fileId, x] of this.own) if (x === w) return fileId;
    return null;
  }

  isMain(w: BrowserWindow | null): boolean { return w !== null && w === this.mainWindow(); }

  // A window of its own made for the file: it takes what the tab was.
  adopt(fileId: string, w: BrowserWindow, handover: Handover): void {
    this.own.set(fileId, w);
    this.handovers.set(w.webContents.id, handover);
  }

  handoverFor(webContentsId: number): Handover | null {
    const h = this.handovers.get(webContentsId) ?? null;
    this.handovers.delete(webContentsId);
    return h;
  }

  // The file goes back to the main window (Attach Tab) or is closed: its window is no longer its own.
  release(fileId: string): BrowserWindow | null {
    const w = this.own.get(fileId) ?? null;
    this.own.delete(fileId);
    return w;
  }

  // A notification: to the window of the file it names.
  route(params: unknown): BrowserWindow | null {
    const fileId = params && typeof params === 'object' ? (params as { fileId?: unknown }).fileId : undefined;
    return this.ownerOf(typeof fileId === 'string' ? fileId : null);
  }
}

// The two halves of a work area: the main window left, the file's own right (View Side by Side).
export function halves(area: { x: number; y: number; width: number; height: number }): { left: typeof area; right: typeof area } {
  const w = Math.floor(area.width / 2);
  return {
    left: { x: area.x, y: area.y, width: w, height: area.height },
    right: { x: area.x + w, y: area.y, width: area.width - w, height: area.height },
  };
}

// Where a tab dragged out lands: its own window a little off the main one (v1: +60, +60), inside the work area.
export function offset(from: { x: number; y: number; width: number; height: number }, area: { x: number; y: number; width: number; height: number }): { x: number; y: number; width: number; height: number } {
  const width = Math.min(from.width, Math.max(760, Math.round(area.width * 0.7)));
  const height = Math.min(from.height, Math.max(480, Math.round(area.height * 0.8)));
  const x = Math.min(from.x + 60, area.x + area.width - width);
  const y = Math.min(from.y + 60, area.y + area.height - height);
  return { x: Math.max(area.x, x), y: Math.max(area.y, y), width, height };
}
