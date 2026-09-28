/* window.app, as src/main/preload.cjs exposes it. */

import type { EngineStatus, Hello, LoadResult, OpenResult, Recovered, RecoveryAsk, WindowMethod } from '../../main/protocol.ts';

// Load Program's options: the memory right-clicked (target), the answer to
// "which memory?" (picks, loading the same file again), an old .s to open next to.
export interface LoadProgramOptions {
  target?: string;
  picks?: Record<string, string>;
  again?: boolean;
  forSource?: string;
}

export interface Opened extends OpenResult {
  path: string;
  already: boolean;     // it was open: go to its tab
  recovered?: boolean;  // opened from its recovery file: unsaved edits (N-19)
}

// A failed call (preload.cjs): the engine's code and data when the engine answered with an error.
export interface CallError {
  name: string;
  message: string;
  code?: number;
  data?: unknown;
}

export interface AppApi {
  call<T = unknown>(method: WindowMethod, params?: Record<string, unknown>): Promise<T>;
  engineStatus(): Promise<EngineStatus>;
  retryEngine(): Promise<EngineStatus>;
  onEngineStatus(listener: (s: EngineStatus) => void): void;
  onNotify(listener: (method: string, params: unknown) => void): void;
  // The engine died and started again: what came back (src/main/recovery.ts, D-142).
  onEngineRecovered(listener: (report: Recovered) => void): void;
  startupFile(): Promise<{ name: string } | null>;      // a .circ named on the command line
  // A file with a recovery file beside it comes back as a question first (N-19): openRecovery answers it.
  openStartupFile(): Promise<Opened | RecoveryAsk | null>;
  openFile(): Promise<Opened | RecoveryAsk | null>;     // the open dialog, then the engine
  openRecovery(id: string, choice: 'recover' | 'discard' | null): Promise<Opened | null>;
  // Leaving (N-19): asked to (the close button, Alt+F4, the PC shutting down); leave() closes the window.
  onLeave(listener: () => void): void;
  leave(): Promise<void>;
  reportDirty(dirty: boolean): Promise<void>;   // whether any file has unsaved changes (the PC's shutdown asks then)
  saveFile(fileId: string, file: { name: string; saveAs?: boolean }): Promise<{ path: string; name: string; bytes: number; needsMipsJar: boolean } | null>;
  // Load Program (N-16): the .hmx dialog in the main process, then mips.load; null: the dialog was cancelled.
  loadProgram(fileId: string, options?: LoadProgramOptions): Promise<LoadResult | null>;
  about(): Promise<AboutInfo>;
  license(index: number): Promise<string>;  // LICENSES[index]; one past the end: Electron's
  openCredits(): Promise<void>;             // LICENSES.chromium.html, in the browser
  setOverlay(color: string | null): Promise<void>;  // the caption buttons' patch; null: white
}

export interface AboutInfo {
  version: string; electron: string; chrome: string; node: string;
  engine: Hello | null;
  licenses: string[]; // titles, in order
}

declare global { interface Window { app: AppApi } }
