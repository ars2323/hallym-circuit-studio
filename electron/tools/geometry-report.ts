/* A developer's view of the N-06 check (canvas-geometry.test.ts): every
   problem, grouped by kind.  node tools/geometry-report.ts [FILTER] */

import { readFileSync } from 'node:fs';
import path from 'node:path';

import type { Component } from '../src/main/protocol.ts';
import { NO_STATE, type PartState } from '../src/renderer/canvas/parts/common.ts';
import { kindOf } from '../src/renderer/canvas/registry.ts';
import { check } from '../tests/unit/canvas-geometry.test.ts';

const cases = (JSON.parse(readFileSync(path.join(import.meta.dirname, '../tests/fixtures/geometry.json'), 'utf8')) as { cases: { case: string; component: Component }[] }).cases;
const filter = process.argv[2] ?? '';
const ones: (c: Component) => PartState = (c) => ({ value: (i) => '1'.repeat(Math.max(1, c.ports[i]?.width ?? 1)), body: undefined });
const byKind = new Map<string, string[]>();
for (const { case: name, component } of cases) {
  if (!name.includes(filter)) continue;
  for (const [label, st] of [['none', NO_STATE], ['ones', ones(component)]] as const) {
    for (const p of check(component, st).problems) {
      const k = kindOf(component);
      byKind.set(k, [...(byKind.get(k) ?? []), `${name} [${label}]: ${p}`]);
    }
  }
}
for (const [k, list] of byKind) {
  console.log(`== ${k}: ${list.length}`);
  for (const l of list.slice(0, 6)) console.log(`   ${l}`);
}
