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

/* The two courses' tutorials (N-18, D-161): each opens a copy of its example
   (in this run's folder, removed after quit) so that the student may change
   it and the example stays as it is; the architecture course's program
   (tutorial.hmx, exported by Hallym MIPS from tutorial.s) goes beside it. */
export const TUTORIALS = {
  logic: ['tutorial-logic.circ'],
  architecture: ['tutorial-mips.circ', 'tutorial.hmx', 'tutorial.s'],
} as const;
export type Course = keyof typeof TUTORIALS;
export const isCourse = (x: unknown): x is Course => x === 'logic' || x === 'architecture';
export const TUTORIAL_PROGRAM = 'tutorial.hmx';

// Where they are: resources/tutorial/ in the package, the repository's tests/tutorial/ in the source tree.
export const tutorialDir = (resources: string | null, repoRoot: string | null): string | null =>
  (resources ? path.join(resources, 'tutorial') : repoRoot ? path.join(repoRoot, 'tests/tutorial') : null);

// What electron-builder copies into resources/examples/ and resources/tutorial/.
export const exampleResources = (repo: string): { from: string; to: string }[] => [
  ...EXAMPLES.map((n) => ({ from: path.join(repo, 'tests/circ', n), to: `examples/${n}` })),
  ...[...new Set<string>([...TUTORIALS.logic, ...TUTORIALS.architecture])].map((n) => ({ from: path.join(repo, 'tests/tutorial', n), to: `tutorial/${n}` })),
];
