/* Where Load Program's dialog opens (N-16, D-147): the main process shows
   the open dialog -- the page never names a path -- and asks the engine to
   load the file the student picked (mips.load).

   The dialog shows executable images only (Executable image (*.hmx), no "All
   files": D-141).  It opens next to the .circ; for an old file whose memory
   still points to a .s (the fact assemblySource), in that .s file's folder
   with the .hmx of the same name filled in -- track A's "Load .hmx for
   sum.s…" (lib-mips LoadProgramMenu). */

import path from 'node:path';

export const IMAGE_FILTER = { name: 'Executable image (*.hmx)', extensions: ['hmx'] };

// A path in the .circ's own words: '/' and '\' both separate, a drive letter or a leading slash is absolute.
const absolute = (p: string): boolean => /^([A-Za-z]:)?[\\/]/.test(p);

// The last name of a path ('/' and '\' both separate), as lib-mips AssemblySource.fileName.
export function fileName(p: string): string {
  const t = p.trim();
  const cut = Math.max(t.lastIndexOf('/'), t.lastIndexOf('\\'));
  return cut < 0 ? t : t.slice(cut + 1);
}

// The executable image of the same name: lab/sum.s -> sum.hmx (lib-mips AssemblySource.imageName).
export function imageName(p: string): string {
  const n = fileName(p);
  const dot = n.lastIndexOf('.');
  return `${dot > 0 ? n.slice(0, dot) : n}.hmx`;
}

/* The dialog's defaultPath: `circ` is the open .circ (null: never saved),
   `source` an old .s path from the file (relative to the .circ's folder).
   undefined: the dialog's own choice. */
export function programDialogPath(circ: string | null, source: string | null): string | undefined {
  const dir = circ ? path.dirname(circ) : undefined;
  if (source && source.trim()) {
    const s = source.trim().replace(/\\/g, '/');
    const at = absolute(s) ? s : dir ? path.join(dir, s) : null;
    if (at) return path.join(path.dirname(at), imageName(s));
  }
  return dir;
}
