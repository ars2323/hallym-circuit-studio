/* Which command starts the engine.  In order:

     HCS_ENGINE_CMD   a JSON array, the whole command (the tests' fake
                      engine: ["node", ".../fake-engine.ts"])
     HCS_ENGINE_JAR   the engine's jar
     next to the app  <resources>/engine/hcs-engine.jar (packaged, N-23), or
                      in the source tree ../engine/build/stage/hcs-engine.jar
                      (./gradlew :engine:stage: hcs-mips.jar beside it), then
                      ../engine/build/libs/hcs-engine.jar (the bundled MIPS
                      library then from ../lib-mips/build/libs/hcs-mips.jar)

   and for a jar, java from HCS_JAVA, then: packaged, only the bundled
   runtime <resources>/runtime (N-04: jlink, Java 21 -- a lab PC's own Java,
   if any, is not used); in the source tree, JAVA_HOME or the PATH.  A
   runtime of ours (engine/build/runtime, the bundled one) has its AppCDS
   archive next to bin/ (hcs-engine.jsa): given to the JVM, it starts the
   engine about a third faster (D-142).

   The lab-PC rule reaches into the JVM too (docs/engine-api.md 1): it runs
   in this run's own folder (removed after quit), without the performance
   data file every JVM writes to the temp folder (-XX:-UsePerfData), with
   its crash report and Java preferences in that folder, and it speaks
   UTF-8 on stdout whatever the system's code page (Korean Windows: MS949).
   The JVM's own warnings (unified logging, stdout by default) go to stderr:
   stdout carries JSON-RPC lines only. */

import { existsSync, readdirSync } from 'node:fs';
import path from 'node:path';

export interface EngineCommand {
  command: string;
  args: string[];
  cwd: string;
  describe: string;   // for the dialog when it does not start
}

export type Located = { ok: true; engine: EngineCommand } | { ok: false; reason: string; looked: string[] };

export interface LocateOptions {
  env: Record<string, string | undefined>;
  runDir: string;                 // this run's folder (cwd, crash reports, prefs)
  resources: string | null;       // the packaged app's resources folder
  repoRoot: string | null;        // the source tree's root (not packaged)
  platform?: NodeJS.Platform;
}

export const APPCDS = 'hcs-engine.jsa';

export function jvmArgs(runDir: string, jar: string, bundledMips: string | null = null, archive: string | null = null): string[] {
  return [
    ...(bundledMips ? [`-Dhcs.bundledMips=${bundledMips}`] : []),
    ...(archive ? [`-XX:SharedArchiveFile=${archive}`] : []),
    '-Xlog:disable', '-Xlog:all=warning:stderr',
    '-Djava.awt.headless=true',
    '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8',
    '-XX:-UsePerfData',
    `-XX:ErrorFile=${path.join(runDir, 'hs_err_pid%p.log')}`,
    `-Djava.util.prefs.userRoot=${path.join(runDir, 'java-prefs')}`,
    `-Djava.io.tmpdir=${path.join(runDir, 'tmp')}`,
    '-jar', jar,
  ];
}

// The java to run, and its AppCDS archive when it is one of our runtimes; a
// reason when the packaged app has none.
function javaFor(o: LocateOptions): { java: string; archive: string | null } | { reason: string; looked: string } {
  const exe = (o.platform ?? process.platform) === 'win32' ? 'java.exe' : 'java';
  // <runtime>/bin/java -> <runtime>/hcs-engine.jsa
  const archiveOf = (java: string) => {
    const a = path.join(path.dirname(path.dirname(java)), APPCDS);
    return path.isAbsolute(java) && existsSync(a) ? a : null;
  };
  if (o.env.HCS_JAVA) return { java: o.env.HCS_JAVA, archive: archiveOf(o.env.HCS_JAVA) };
  if (o.resources) {
    const bundled = path.join(o.resources, 'runtime', 'bin', exe);
    if (existsSync(bundled)) return { java: bundled, archive: archiveOf(bundled) };
    return { reason: `Java 런타임이 없습니다: ${path.join('runtime', 'bin', exe)}`, looked: bundled };
  }
  if (o.env.JAVA_HOME) {
    const home = path.join(o.env.JAVA_HOME, 'bin', exe);
    if (existsSync(home)) return { java: home, archive: null };
  }
  return { java: 'java', archive: null };
}

export const ENGINE_JAR = 'hcs-engine.jar';

// Another engine jar in the libs folder (a build under another name): the one without -sources/-javadoc/-plain.
function otherJar(dir: string): string | null {
  let names: string[];
  try { names = readdirSync(dir); } catch { return null; }
  const jar = names.filter((n) => n.endsWith('.jar') && !/-(sources|javadoc|plain)\.jar$/.test(n)).sort()[0];
  return jar ? path.join(dir, jar) : null;
}

export function locateEngine(o: LocateOptions): Located {
  if (o.env.HCS_ENGINE_CMD) {
    let cmd: unknown;
    try { cmd = JSON.parse(o.env.HCS_ENGINE_CMD); } catch { cmd = null; }
    if (!Array.isArray(cmd) || cmd.length === 0 || !cmd.every((c) => typeof c === 'string')) {
      return { ok: false, reason: '엔진 명령을 읽을 수 없습니다(HCS_ENGINE_CMD: 문자열 JSON 배열)', looked: [o.env.HCS_ENGINE_CMD] };
    }
    const [command, ...args] = cmd as string[];
    return { ok: true, engine: { command, args, cwd: o.runDir, describe: (cmd as string[]).join(' ') } };
  }
  const looked: string[] = [];
  let jar: string | null = null;
  let mips: string | null = null;
  const found = (p: string) => { looked.push(p); return existsSync(p) ? p : null; };
  if (o.env.HCS_ENGINE_JAR) {
    jar = found(o.env.HCS_ENGINE_JAR);
  } else {
    if (o.resources) jar = found(path.join(o.resources, 'engine', ENGINE_JAR));
    if (!jar && o.repoRoot) jar = found(path.join(o.repoRoot, 'engine', 'build', 'stage', ENGINE_JAR));
    if (!jar && o.repoRoot) {
      const libs = path.join(o.repoRoot, 'engine', 'build', 'libs');
      jar = found(path.join(libs, ENGINE_JAR)) ?? otherJar(libs);
      const built = path.join(o.repoRoot, 'lib-mips', 'build', 'libs', 'hcs-mips.jar');
      if (jar && existsSync(built)) mips = built;
    }
  }
  // hcs-mips.jar beside the engine (packaged, staged) is named to it outright
  // (-Dhcs.bundledMips): the engine need not find it through its own code
  // source, which an AppCDS archive made without the same flag hides (D-142).
  if (jar && !mips) {
    const beside = path.join(path.dirname(jar), 'hcs-mips.jar');
    if (existsSync(beside)) mips = beside;
  }
  // The file named is the one looked for (no particle after a name: the name after a colon).
  if (!jar) return { ok: false, reason: `엔진 파일이 없습니다: ${path.basename(looked[0] ?? ENGINE_JAR)}`, looked };
  const runtime = javaFor(o);
  if ('reason' in runtime) return { ok: false, reason: runtime.reason, looked: [...looked, runtime.looked] };
  const args = jvmArgs(o.runDir, jar, mips, runtime.archive);
  return { ok: true, engine: { command: runtime.java, args, cwd: o.runDir, describe: `${runtime.java} -jar ${jar}` } };
}
