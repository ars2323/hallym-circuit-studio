/* The lab-PC rule (Hallym MIPS's, src/main/main.ts there): nothing is kept
   from one run to the next.  Chromium needs a profile folder while it runs;
   each run gets a new one, <temp>/HallymCircuitStudio/run-<pid>-<time>,
   removed when the program has quit.  One a run could not remove (Windows
   keeps files open until the process is gone) is removed at the next start,
   once its process is no longer running.  The engine runs in the same
   folder (engine-locate.ts).  HCS_USER_DATA puts the run folders somewhere
   else: the tests look into it. */

import { readdirSync, rmSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';

export const runsDirFor = (env: Record<string, string | undefined>): string =>
  env.HCS_USER_DATA ?? path.join(os.tmpdir(), 'HallymCircuitStudio');

export const runDirName = (pid: number, now: number): string => `run-${pid}-${now}`;

export const alive = (pid: number): boolean => {
  try { process.kill(pid, 0); return true; } catch (e) { return (e as NodeJS.ErrnoException).code === 'EPERM'; }
};

// Removes the folders of earlier runs whose process is gone; returns their names.
export function removeEarlierRuns(runsDir: string, self: number, isAlive: (pid: number) => boolean = alive): string[] {
  let names: string[];
  try { names = readdirSync(runsDir); } catch { return []; }
  const removed: string[] = [];
  for (const name of names) {
    const pid = Number(/^run-(\d+)-\d+$/.exec(name)?.[1]);
    if (!pid || pid === self || isAlive(pid)) continue;
    rmSync(path.join(runsDir, name), { recursive: true, force: true });
    removed.push(name);
  }
  return removed;
}

/* Chromium writes into the folder until its very end, so the folder is
   removed after the program has exited: by the same executable run as
   plain Node, detached, waiting for this process to be gone (at most 15 s).
   This is the script it runs. */
export function removeAfterExitScript(pid: number, dir: string): string {
  return `const {rmSync}=require('fs');const pid=${pid};const dir=${JSON.stringify(dir)};
const gone=()=>{try{process.kill(pid,0);return false}catch(e){return e.code!=='EPERM'}};
const t0=Date.now();(function wait(){if(gone()||Date.now()-t0>15000){try{rmSync(dir,{recursive:true,force:true,maxRetries:5,retryDelay:200})}catch{}}else setTimeout(wait,100)})();`;
}

/* The .circ file named on the command line, if any: the first argument
   that is not a switch and ends in .circ, resolved against the folder the
   program was started in.  (In the source tree the arguments start with
   Electron and src/main/main.ts; packaged, with the executable.) */
export function circArgument(argv: readonly string[], cwd: string): string | null {
  for (const a of argv.slice(1)) {
    if (a.startsWith('-')) continue;
    if (/\.circ$/i.test(a)) return path.resolve(cwd, a);
  }
  return null;
}
