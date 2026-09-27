/* Measures the engine as the app starts it (N-04, D-142): the time from
   launching java to the answer to engine.hello, its memory with nothing
   open and with ref-mips open (every circuit's model asked for, as the
   window does), and the bundled runtime's size -- on this OS.  CI runs it
   on Linux and Windows and prints the numbers in the job's log and summary.

     node tools/engine-measure.ts [--runs N] [--java <java>] [--json <file>]

   The engine is launched exactly as src/main/engine-locate.ts launches it
   (the source tree's engine/build/stage jars; java from --java, HCS_JAVA,
   or engine/build/runtime).  With the bundled runtime it is measured with
   its AppCDS archive (as shipped) and without it (the archive's worth).
   Memory is the resident set (Linux /proc VmRSS; Windows the working set,
   tasklist).  Each figure is the median of the runs. */

import { execFileSync, spawn } from 'node:child_process';
import { appendFileSync, existsSync, mkdtempSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

import { EngineClient, type EngineProcess } from '../src/main/engine.ts';
import { locateEngine } from '../src/main/engine-locate.ts';
import type { OpenResult } from '../src/main/protocol.ts';

const root = path.join(import.meta.dirname, '..');
const repo = path.join(root, '..');
const arg = (name: string) => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : undefined; };
const runs = Number(arg('--runs') ?? 5);
const exe = process.platform === 'win32' ? 'java.exe' : 'java';
const runtimeDir = path.join(repo, 'engine/build/runtime');
const java = arg('--java') ?? process.env.HCS_JAVA ?? path.join(runtimeDir, 'bin', exe);
const REF_MIPS = path.join(repo, 'tests/mips/ref-mips.circ');

function rssMb(pid: number): number | null {
  try {
    if (process.platform === 'linux') {
      const m = /VmRSS:\s+(\d+) kB/.exec(readFileSync(`/proc/${pid}/status`, 'utf8'));
      return m ? Math.round(Number(m[1]) / 1024) : null;
    }
    if (process.platform === 'win32') {
      // "java.exe","1234","Console","1","123,456 K"
      const line = execFileSync('tasklist', ['/FI', `PID eq ${pid}`, '/FO', 'CSV', '/NH'], { encoding: 'utf8' });
      const m = /"([\d.,\s]+)\s*K"\s*$/m.exec(line.trim());
      return m ? Math.round(Number(m[1].replace(/[^\d]/g, '')) / 1024) : null;
    }
  } catch { /* gone */ }
  return null;
}

const pause = (ms: number) => new Promise((done) => setTimeout(done, ms));
const median = (xs: number[]) => { const s = [...xs].sort((a, b) => a - b); return s[Math.floor(s.length / 2)]; };

interface Run { hello: number; open: number; rssEmpty: number | null; rssOpen: number | null }

async function once(withArchive: boolean): Promise<Run> {
  const runDir = mkdtempSync(path.join(tmpdir(), 'hcs-measure-'));
  const env = { ...process.env, HCS_JAVA: java, HCS_ENGINE_CMD: '', HCS_ENGINE_JAR: '' };
  const located = locateEngine({ env, runDir, resources: null, repoRoot: repo });
  if (!located.ok) throw new Error(`${located.reason}: ${located.looked.join(', ')}`);
  const args = withArchive ? located.engine.args : located.engine.args.filter((a) => !a.startsWith('-XX:SharedArchiveFile='));
  let t0 = 0;
  const engine = new EngineClient({
    client: { client: 'engine-measure', version: '0' },
    launch: (): EngineProcess => { t0 = performance.now(); return spawn(located.engine.command, args, { cwd: runDir, stdio: 'pipe', windowsHide: true }); },
  });
  try {
    await engine.start();
    const hello = performance.now() - t0;
    await pause(1500);
    const rssEmpty = rssMb(engine.pid!);
    const t1 = performance.now();
    const o = await engine.call<OpenResult>('file.open', { path: REF_MIPS });
    for (const c of o.circuits) await engine.call('model.circuit', { fileId: o.fileId, circuitId: c.circuitId });
    const open = performance.now() - t1;
    await pause(1500);
    const rssOpen = rssMb(engine.pid!);
    return { hello, open, rssEmpty, rssOpen };
  } finally {
    await engine.shutdown();
    rmSync(runDir, { recursive: true, force: true });
  }
}

async function series(withArchive: boolean): Promise<Record<string, number | null>> {
  const all: Run[] = [];
  for (let i = 0; i < runs; i += 1) all.push(await once(withArchive));
  const med = (k: keyof Run) => { const xs = all.map((r) => r[k]).filter((x): x is number => x !== null); return xs.length ? Math.round(median(xs)) : null; };
  return { helloMs: med('hello'), openRefMipsMs: med('open'), rssEmptyMb: med('rssEmpty'), rssRefMipsMb: med('rssOpen') };
}

function sizeOf(dir: string): number {
  let n = 0;
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    n += e.isDirectory() ? sizeOf(p) : statSync(p).size;
  }
  return n;
}

const bundled = path.resolve(java) === path.resolve(path.join(runtimeDir, 'bin', exe));
const archive = existsSync(path.join(path.dirname(path.dirname(java)), 'hcs-engine.jsa'));
const out: Record<string, unknown> = { platform: `${process.platform}-${process.arch}`, java, runs };
if (bundled) {
  const zips = existsSync(path.join(repo, 'engine/build/distributions'))
    ? readdirSync(path.join(repo, 'engine/build/distributions')).filter((f) => /^hcs-runtime-.*\.zip$/.test(f)) : [];
  out.runtimeMb = Math.round(sizeOf(runtimeDir) / 1048576);
  if (zips[0]) out.runtimeZipMb = Math.round(statSync(path.join(repo, 'engine/build/distributions', zips[0])).size / 1048576);
  const release = readFileSync(path.join(runtimeDir, 'release'), 'utf8');
  out.modules = /MODULES="([^"]*)"/.exec(release)?.[1]?.split(' ');
}
out.withAppCds = archive ? await series(true) : null;
out.withoutAppCds = await series(false);

console.log(JSON.stringify(out, null, 2));
const json = arg('--json');
if (json) writeFileSync(json, `${JSON.stringify(out, null, 2)}\n`);
if (process.env.GITHUB_STEP_SUMMARY) {
  const row = (name: string, r: Record<string, number | null> | null) =>
    r ? `| ${name} | ${r.helloMs} ms | ${r.openRefMipsMs} ms | ${r.rssEmptyMb} MB | ${r.rssRefMipsMb} MB |` : '';
  appendFileSync(process.env.GITHUB_STEP_SUMMARY, [
    `### Engine (${out.platform}, ${bundled ? 'bundled runtime' : java})`, '',
    bundled ? `Runtime: ${out.runtimeMb} MB on disk, ${out.runtimeZipMb ?? '?'} MB zipped` : '', '',
    '| | start → engine.hello | open ref-mips | RSS empty | RSS ref-mips |', '|---|---|---|---|---|',
    row('with AppCDS', out.withAppCds as Record<string, number | null> | null), row('without AppCDS', out.withoutAppCds as Record<string, number | null>), '',
  ].filter((l, i, a) => l !== '' || a[i - 1] !== '').join('\n'));
}
