/* The engine beside the packaged app (N-04, D-142): tools/package.ts stages
   the engine's jars and its bundled Java runtime before electron-builder
   runs, and electron-builder copies them into resources/ (extraResources),
   where src/main/engine-locate.ts looks for them.

     <out>/engine/   hcs-engine.jar, hcs-mips.jar   (./gradlew :engine:stage)
     <out>/runtime/  the jlink runtime with its AppCDS archive, hcs-engine.jsa
                     (./gradlew :engine:runtime, on the OS packaged for)

   Kept apart from package.ts so that tests/unit/package.test.ts can run it
   with paths of its own, without electron-builder. */

import { cpSync, existsSync, rmSync } from 'node:fs';
import path from 'node:path';

const repo = path.join(import.meta.dirname, '..', '..');

export interface StagePaths {
  stage: string;        // the engine's jars (engine/build/stage)
  runtime: string;      // the bundled runtime (engine/build/runtime)
  out: string;          // where they are staged (electron/build/package)
  platform?: NodeJS.Platform;
}

export const DEFAULT_STAGE_PATHS: StagePaths = {
  stage: path.join(repo, 'engine/build/stage'),
  runtime: path.join(repo, 'engine/build/runtime'),
  out: path.join(repo, 'electron/build/package'),
};

// What electron-builder copies into resources/ (package.ts config.extraResources).
export const extraResources = (out: string): { from: string; to: string }[] => [
  { from: path.join(out, 'engine'), to: 'engine' },
  { from: path.join(out, 'runtime'), to: 'runtime' },
];

// Stages the engine and its runtime; throws, naming the Gradle task, when one is missing.
export function stageEngine(p: StagePaths = DEFAULT_STAGE_PATHS): void {
  const javaExe = (p.platform ?? process.platform) === 'win32' ? 'java.exe' : 'java';
  const needs: [string, string][] = [
    [path.join(p.stage, 'hcs-engine.jar'), ':engine:stage'],
    [path.join(p.stage, 'hcs-mips.jar'), ':engine:stage'],
    [path.join(p.runtime, 'bin', javaExe), ':engine:runtime'],
  ];
  for (const [need, task] of needs) {
    if (!existsSync(need)) throw new Error(`${need} is missing: ./gradlew ${task} (on this OS) first`);
  }
  for (const d of ['engine', 'runtime']) rmSync(path.join(p.out, d), { recursive: true, force: true });
  // Symbolic links stay as they are (the runtime's legal/<module>/LICENSE -> ../java.base/LICENSE on Linux).
  const how = { recursive: true, preserveTimestamps: true, verbatimSymlinks: true };
  cpSync(p.stage, path.join(p.out, 'engine'), how);
  cpSync(p.runtime, path.join(p.out, 'runtime'), how);
}
