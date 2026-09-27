/* window.app, as src/main/preload.cjs exposes it. */

import type { EngineStatus, Hello, OpenResult, WindowMethod } from '../../main/protocol.ts';

export interface Opened extends OpenResult {
  path: string;
  already: boolean;     // it was open: go to its tab
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
  startupFile(): Promise<{ name: string } | null>;      // a .circ named on the command line
  openStartupFile(): Promise<Opened | null>;
  openFile(): Promise<Opened | null>;                   // the open dialog, then the engine
  saveFile(fileId: string, file: { name: string; saveAs?: boolean }): Promise<{ path: string; name: string; bytes: number } | null>;
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
