/* Circuits from other files and libraries (N-11, D-153; v1 P-03, P-05;
   docs/engine-api.md "회로·모양·라이브러리·다른 파일"): the calls that take a
   path.  The page never names a path (D-135): the file dialog is here, and
   the path goes from here to the engine.

     circuits:importChoose  File › Import Subcircuits…: the .circ dialog, then
                            the engine's list of its circuits (file.peek); the
                            path is kept here for the next two calls
     circuits:importPlan    what would come in (model.importPlan)
     circuits:importApply   the import (edit.importCircuits: one undo step,
                            journaled for a recovery with its absolute path)
     library:load           Project › Load Library › Built-in (a name, no
                            dialog), Logisim Library… (.circ), JAR Library… (.jar)
     library:useOpenFile    another open file as a library (the tab dragged
                            onto the Canvas, the Components list's Open Files):
                            its saved path
     file:editOriginal      a library circuit's file, opened (or its tab) --
                            v1 Edit Original File */

import type { BrowserWindow, Dialog, IpcMainInvokeEvent } from 'electron';
import path from 'node:path';

import type { EditResult, ImportPeek, ImportPlan, OpenResult, RecoveryAsk } from './protocol.ts';

export interface CircuitFilesHost {
  dialog: Pick<Dialog, 'showOpenDialog'>;
  windowCall<T>(method: string, params: unknown): Promise<T>;
  openFiles: Map<string, string | null>;
  // the file, or first the question of its recovery file (N-19): the page answers it as for File › Open
  openPath(p: string): Promise<(OpenResult & { path: string; already: boolean }) | RecoveryAsk>;
  parent(e: IpcMainInvokeEvent): BrowserWindow;
  handle(channel: string, f: (e: IpcMainInvokeEvent, ...args: unknown[]) => unknown): void;
}

const CIRC = { name: 'Logisim circuit', extensions: ['circ'] };
const JAR = { name: 'JAR library', extensions: ['jar'] };

// The folder a dialog opens in: the file's own (the saved one), else none (the system's choice).
export function dialogFolder(saved: string | null | undefined): string | undefined {
  return saved ? path.dirname(saved) : undefined;
}

export function registerCircuitFiles(h: CircuitFilesHost): void {
  const importing = new Map<string, string>();   // fileId → the .circ chosen to import from

  const known = (fileId: unknown): string => {
    if (typeof fileId !== 'string' || !h.openFiles.has(fileId)) throw new Error(`no open file ${String(fileId)}`);
    return fileId;
  };
  const names = (circuits: unknown): string[] => {
    if (!Array.isArray(circuits) || !circuits.every((c) => typeof c === 'string')) throw new Error('circuits must be names');
    return circuits as string[];
  };

  h.handle('circuits:importChoose', async (e, fileId) => {
    const id = known(fileId);
    const r = await h.dialog.showOpenDialog(h.parent(e), {
      title: 'Import Subcircuits', defaultPath: dialogFolder(h.openFiles.get(id)), filters: [CIRC], properties: ['openFile'],
    });
    if (r.canceled || r.filePaths.length === 0) return null;
    const file = path.resolve(r.filePaths[0]);
    const peek = await h.windowCall<ImportPeek>('file.peek', { fileId: id, path: file });
    importing.set(id, file);
    return peek;
  });
  h.handle('circuits:importPlan', async (_e, fileId, circuits) => {
    const id = known(fileId);
    const file = importing.get(id);
    if (!file) throw new Error('no file chosen to import from');
    return h.windowCall<ImportPlan>('model.importPlan', { fileId: id, path: file, circuits: names(circuits) });
  });
  h.handle('circuits:importApply', async (_e, fileId, circuits) => {
    const id = known(fileId);
    const file = importing.get(id);
    if (!file) throw new Error('no file chosen to import from');
    return h.windowCall<EditResult & { plan?: ImportPlan }>('edit.importCircuits', { fileId: id, path: file, circuits: names(circuits) });
  });

  h.handle('library:load', async (e, fileId, kind, name) => {
    const id = known(fileId);
    if (kind === 'builtin') {
      if (typeof name !== 'string') throw new Error('a built-in library needs a name');
      return h.windowCall<EditResult>('edit.loadLibrary', { fileId: id, kind: 'builtin', name });
    }
    if (kind !== 'circ' && kind !== 'jar') throw new Error(`no kind of library ${String(kind)}`);
    const r = await h.dialog.showOpenDialog(h.parent(e), {
      title: kind === 'circ' ? 'Load Logisim Library' : 'Load JAR Library',
      defaultPath: dialogFolder(h.openFiles.get(id)), filters: [kind === 'circ' ? CIRC : JAR], properties: ['openFile'],
    });
    if (r.canceled || r.filePaths.length === 0) return null;
    return h.windowCall<EditResult>('edit.loadLibrary', { fileId: id, kind, path: path.resolve(r.filePaths[0]) });
  });

  h.handle('library:useOpenFile', async (_e, fileId, otherFileId) => {
    const id = known(fileId);
    const other = known(otherFileId);
    const saved = h.openFiles.get(other);
    if (!saved) throw Object.assign(new Error('the file was never saved'), { name: 'Unsaved' });
    return h.windowCall<EditResult>('edit.loadLibrary', { fileId: id, kind: 'circ', path: path.resolve(saved) });
  });

  h.handle('file:editOriginal', async (_e, fileId, circuitId) => {
    const id = known(fileId);
    const o = await h.windowCall<{ path: string | null; circuit: string }>('file.originOf', { fileId: id, circuitId });
    if (!o.path) return null;
    const opened = await h.openPath(o.path);
    return { ...opened, circuit: o.circuit };
  });
}
