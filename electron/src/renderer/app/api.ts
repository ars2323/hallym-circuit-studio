/* window.app, as src/main/preload.cjs exposes it. */

import type { EditResult, SubmissionPlan, EngineStatus, Hello, ImportPeek, ImportPlan, LoadResult, OpenResult, Recovered, RecoveryAsk, WindowMethod } from '../../main/protocol.ts';
import type { Handover } from '../../main/windows.ts';

// Load Program's options: the memory right-clicked (target), the answer to
// "which memory?" (picks, loading the same file again), an old .s to open next to.
export interface LoadProgramOptions {
  target?: string;
  picks?: Record<string, string>;
  again?: boolean;
  forSource?: string;
}

// A RAM's or a ROM's image (N-10): which memory, as the engine names it, and load or save.
export interface MemoryImageOptions {
  circuitId: string;      // the memory's circuit
  root: string;           // the circuit the view starts from, and the instances gone into (a RAM's state)
  path: string[];
  componentId: string;
  kind: 'ram' | 'rom';
  mode: 'load' | 'save';
}

export interface Opened extends OpenResult {
  path: string;
  already: boolean;     // it was open: go to its tab
  recovered?: boolean;  // opened from its recovery file: unsaved edits (N-19)
  readOnly?: boolean;   // an example, opened read-only: Save asks where (D-158)
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
  // Load Image… / Save Image… of a RAM or a ROM (N-10): the file dialog in the main process, then the engine; null: cancelled.
  memoryImage(fileId: string, options: MemoryImageOptions): Promise<unknown>;
  about(): Promise<AboutInfo>;
  license(index: number): Promise<string>;  // LICENSES[index]; one past the end: Electron's
  openCredits(): Promise<void>;             // LICENSES.chromium.html, in the browser
  setOverlay(patch: { color: string; symbolColor: string }): Promise<void>;  // the caption buttons' patch and symbols (shared/overlay.ts)
  // N-11 (src/main/circuit-files.ts): Import Subcircuits (the .circ dialog, then its circuits; null: cancelled),
  // its plan and the import; Load Library (builtin by name; circ and jar through a dialog); another open file
  // as a library; Edit Original File.
  importChoose(fileId: string): Promise<ImportPeek | null>;
  importPlan(fileId: string, circuits: string[]): Promise<ImportPlan>;
  importApply(fileId: string, circuits: string[]): Promise<EditResult & { plan?: ImportPlan }>;
  loadLibrary(fileId: string, kind: 'builtin' | 'circ' | 'jar', name?: string): Promise<(EditResult & { lib?: string }) | null>;
  useOpenFile(fileId: string, otherFileId: string): Promise<EditResult & { lib?: string }>;
  openFilesAll(): Promise<{ fileId: string; path: string | null }[]>;        // every open file, in any window
  onFilesChanged(listener: (list: { fileId: string; path: string | null }[]) => void): void;
  openDropped(files: File[]): Promise<(Opened | RecoveryAsk)[]>;   // .circ files dropped on the window (I-181)
  editOriginal(fileId: string, circuitId: string): Promise<((Opened | RecoveryAsk) & { circuit: string }) | null>;
  // N-21 (src/main/pictures.ts): Export Image… (the save dialog, then the file; null: cancelled), Print… (the
  // system's print dialog), Create Submission… (its checks; the save dialog, then the zip).
  exportPicture(fileId: string, o: { format: 'svg' | 'png' | 'pdf'; svg: string; name: string; scale?: number }): Promise<{ name: string; bytes: number; scale: number } | null>;
  printPictures(pages: { svg: string; name: string }[], o: { header: string; rotate: boolean }): Promise<{ printed: boolean; pages: number }>;
  submissionPlan(fileId: string): Promise<SubmissionPlan>;
  submissionWrite(fileId: string): Promise<SubmissionPlan | null>;
  // N-11 (src/main/windows.ts): this window (the main one, or a file's own and what it starts from), Detach Tab /
  // View Side by Side, Attach Tab, a window of its own closed after its file, the close question cancelled.
  windowRole(): Promise<{ main: boolean; handover: Handover | null }>;
  detach(fileId: string, handover: Handover, how: 'window' | 'side'): Promise<boolean>;
  attach(handover: Handover): Promise<boolean>;
  windowClosed(): Promise<boolean>;
  closeCancelled(): void;
  onCloseRequest(listener: () => void): void;
  onAdopt(listener: (handover: Handover) => void): void;
  // Help › Examples (D-158): the program's circuits by name; one opened read-only.
  examples(): Promise<{ id: string; name: string; course: 'logic' | 'architecture' }[]>;
  openExample(id: string): Promise<Opened | RecoveryAsk | null>;
  // File › Open Recent (I-130): this run's files by name (the paths stay in the main process).
  recentFiles(): Promise<{ id: string; name: string }[]>;
  openRecent(id: string): Promise<Opened | RecoveryAsk | null>;
  minimize(): Promise<void>;
  maximize(): Promise<void>;
}

export interface AboutInfo {
  version: string; electron: string; chrome: string; node: string;
  engine: Hello | null;
  licenses: string[]; // titles, in order
}

declare global { interface Window { app: AppApi } }
