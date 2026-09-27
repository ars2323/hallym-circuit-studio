/* Which command starts the engine.  In order:

     HCS_ENGINE_CMD   a JSON array, the whole command (the tests' fake
                      engine: ["node", ".../fake-engine.ts"])
     HCS_ENGINE_JAR   the engine's jar
     next to the app  <resources>/engine/hcs-engine.jar (packaged, N-23), or
                      in the source tree ../engine/build/stage/hcs-engine.jar
                      (./gradlew :engine:stage: hcs-mips.jar beside it), then
                      ../engine/build/libs/hcs-engine.jar (the bundled MIPS
                      library then from ../lib-mips/build/libs/hcs-mips.jar)

   and for a jar, java from HCS_JAVA, <resources>/runtime (the bundled
   runtime, N-04), JAVA_HOME, or the PATH.

   The lab-PC rule reaches into the JVM too (docs/engine-api.md 1): it runs
   in this run's own folder (removed after quit), without the performance
   data file every JVM writes to the temp folder (-XX:-UsePerfData), with
   its crash report and Java preferences in that folder, and it speaks
   UTF-8 on stdout whatever the system's code page (Korean Windows: MS949). */

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

export function jvmArgs(runDir: string, jar: string, bundledMips: string | null = null): string[] {
  return [
    ...(bundledMips ? [`-Dhcs.bundledMips=${bundledMips}`] : []),
    '-Djava.awt.headless=true',
    '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8',
    '-XX:-UsePerfData',
    `-XX:ErrorFile=${path.join(runDir, 'hs_err_pid%p.log')}`,
    `-Djava.util.prefs.userRoot=${path.join(runDir, 'java-prefs')}`,
    `-Djava.io.tmpdir=${path.join(runDir, 'tmp')}`,
    '-jar', jar,
  ];
}

function javaFor(o: LocateOptions): string {
  const exe = (o.platform ?? process.platform) === 'win32' ? 'java.exe' : 'java';
  if (o.env.HCS_JAVA) return o.env.HCS_JAVA;
  if (o.resources) {
    const bundled = path.join(o.resources, 'runtime', 'bin', exe);
    if (existsSync(bundled)) return bundled;
  }
  if (o.env.JAVA_HOME) {
    const home = path.join(o.env.JAVA_HOME, 'bin', exe);
    if (existsSync(home)) return home;
  }
  return 'java';
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
  // The file named is the one looked for (no particle after a name: the name after a colon).
  if (!jar) return { ok: false, reason: `엔진 파일이 없습니다: ${path.basename(looked[0] ?? ENGINE_JAR)}`, looked };
  const java = javaFor(o);
  const args = jvmArgs(o.runDir, jar, mips);
  return { ok: true, engine: { command: java, args, cwd: o.runDir, describe: `${java} -jar ${jar}` } };
}
