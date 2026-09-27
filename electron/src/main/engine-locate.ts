/* Which command starts the engine.  In order:

     HCS_ENGINE_CMD   a JSON array, the whole command (the tests' fake
                      engine: ["node", ".../fake-engine.ts"])
     HCS_ENGINE_JAR   the engine's jar
     next to the app  <resources>/engine/engine.jar (packaged, N-23), or
                      ../engine/build/libs/*.jar (the source tree, N-03)

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

export function jvmArgs(runDir: string, jar: string): string[] {
  return [
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

// The engine module's jar in the source tree: the one without -sources/-javadoc/-plain.
function builtJar(repoRoot: string): string | null {
  const dir = path.join(repoRoot, 'engine', 'build', 'libs');
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
      return { ok: false, reason: 'HCS_ENGINE_CMD는 문자열 JSON 배열이어야 합니다', looked: [o.env.HCS_ENGINE_CMD] };
    }
    const [command, ...args] = cmd as string[];
    return { ok: true, engine: { command, args, cwd: o.runDir, describe: (cmd as string[]).join(' ') } };
  }
  const looked: string[] = [];
  let jar: string | null = null;
  if (o.env.HCS_ENGINE_JAR) {
    looked.push(o.env.HCS_ENGINE_JAR);
    if (existsSync(o.env.HCS_ENGINE_JAR)) jar = o.env.HCS_ENGINE_JAR;
  } else {
    if (o.resources) {
      const packaged = path.join(o.resources, 'engine', 'engine.jar');
      looked.push(packaged);
      if (existsSync(packaged)) jar = packaged;
    }
    if (!jar && o.repoRoot) {
      looked.push(path.join(o.repoRoot, 'engine', 'build', 'libs', '*.jar'));
      jar = builtJar(o.repoRoot);
    }
  }
  if (!jar) return { ok: false, reason: '엔진 파일(engine.jar)을 찾지 못했습니다', looked };
  const java = javaFor(o);
  const args = jvmArgs(o.runDir, jar);
  return { ok: true, engine: { command: java, args, cwd: o.runDir, describe: `${java} -jar ${jar}` } };
}
