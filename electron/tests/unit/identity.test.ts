/* The program's license is said the same way everywhere: GNU GPL, version
   2 or later (Logisim 2.7.1's) -- the repository's LICENSE and NOTICE, the
   package, the packaged app's own package.json, and About. */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

const root = path.join(import.meta.dirname, '..', '..');
const read = (p: string) => readFileSync(path.join(root, p), 'utf8');

test('GPL, version 2 or later: LICENSE, NOTICE, package.json and its lock, tools/package.ts, About', () => {
  assert.match(read('../LICENSE'), /GNU GENERAL PUBLIC LICENSE\s+Version 2, June 1991/);
  assert.match(read('../NOTICE'), /GNU General Public License, version 2 or \(at your option\) any later version/);
  assert.equal(JSON.parse(read('package.json')).license, 'GPL-2.0-or-later');
  assert.equal(JSON.parse(read('package-lock.json')).packages[''].license, 'GPL-2.0-or-later');
  assert.match(read('tools/package.ts'), /license: 'GPL-2\.0-or-later'/);
  assert.match(read('src/main/paths.ts'), /GNU General Public License, version 2 or later/);
  assert.match(read('src/renderer/app/app.ts'), /Based on Logisim 2\.7\.1 by Carl Burch \(GNU GPL, version 2 or later\)/);
});
