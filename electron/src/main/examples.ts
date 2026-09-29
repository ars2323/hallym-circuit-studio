/* Help › Examples (v1 V-07, D-102; v2 D-158): the circuits that come with
   the program, opened read-only -- a student looks around first, and Save
   asks where (Save As), so an example is never written over.  The same
   files as the repository's tests/circ/ (the tests keep them working); the
   package carries them in resources/examples/ (tools/package-config.ts).

   Each belongs to a course (A-08, D-168): Help › Examples lists the course
   on show's; the other course's after switching. */

import path from 'node:path';

export const EXAMPLES = ['adder-1bit.circ', 'ripple-carry-4bit.circ', 'counter-4bit.circ', 'demo-datapath.circ', 'console-demo.circ', 'stack-demo.circ'] as const;
export type Example = typeof EXAMPLES[number];
// 논리설계 및 실험 (logic): gates, a subcircuit, a register and a clock; 컴퓨터구조 (architecture): the MIPS parts.
export const EXAMPLE_COURSE: Readonly<Record<Example, 'logic' | 'architecture'>> = {
  'adder-1bit.circ': 'logic', 'ripple-carry-4bit.circ': 'logic', 'counter-4bit.circ': 'logic',
  'demo-datapath.circ': 'architecture', 'console-demo.circ': 'architecture', 'stack-demo.circ': 'architecture',
};

export const isExample = (id: string): id is Example => (EXAMPLES as readonly string[]).includes(id);

// Where they are: resources/examples/ in the package, the repository's tests/circ/ in the source tree.
export const examplesDir = (resources: string | null, repoRoot: string | null): string | null =>
  (resources ? path.join(resources, 'examples') : repoRoot ? path.join(repoRoot, 'tests/circ') : null);

// What electron-builder copies into resources/examples/.
export const exampleResources = (repo: string): { from: string; to: string }[] =>
  EXAMPLES.map((n) => ({ from: path.join(repo, 'tests/circ', n), to: `examples/${n}` }));
