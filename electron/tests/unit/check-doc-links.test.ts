/* tools/check-doc-links.ts (taken from Hallym MIPS, derived; D-174): broken
   links and anchors, and pictures no document shows. */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import { anchors, brokenLinks, isDocPicture, links, orphanPictures, UPSTREAM } from '../../tools/check-doc-links.ts';

test('links: Markdown links, pictures, <img>, references; not inside code', () => {
  const md = '[a](x.md) ![b](p.png "t") <img src="q.png"> `[c](no.md)`\n```\n[d](no2.md)\n```\n[ref]: r.md\n[w-1]…[0](I-189)\n';
  assert.deepEqual(links(md), ['x.md', 'p.png', 'I-189', 'q.png', 'r.md']);
});

test('anchors: GitHub\'s, Korean kept, repeats numbered', () => {
  assert.deepEqual([...anchors('# 6.8 설치하기\n## `code` Title!\n## Title\n```\n# not\n```\n')], ['68-설치하기', 'code-title', 'title']);
  assert.deepEqual([...anchors('## A\n## A\n')], ['a', 'a-1']);
});

test('broken: a missing file, a missing heading; web links are not checked', () => {
  const files: Record<string, string> = {
    'docs/a.md': '[ok](b.md#title) [bad](b.md#nope) [gone](c.md) [web](https://x.org/y) [self](#here)\n# Here\n',
    'docs/b.md': '# Title\n',
  };
  const r = brokenLinks(Object.keys(files), (f) => files[f], (f) => f in files);
  assert.equal(r.count, 4);
  assert.deepEqual(r.broken, ['docs/a.md: b.md#nope -- no heading #nope in docs/b.md', 'docs/a.md: c.md -- no such file']);
});

test('orphans: a picture no document names; named by path, relative path, or by name in its folder\'s README', () => {
  const pictures = ['electron/docs/screens/a.png', 'electron/docs/screens/b.png', 'docs/img/c.png', 'docs/img/d.png'];
  const docs = [
    { f: 'electron/docs/screens/README.md', text: '| `a.png` | the first screen |' },
    { f: 'docs/guide.md', text: '![c](img/c.png)' },
    { f: 'README.md', text: 'b.png is written by tools/capture-screens.ts' },   // a name elsewhere is not a use
  ];
  assert.deepEqual(orphanPictures(pictures, docs), ['electron/docs/screens/b.png', 'docs/img/d.png']);
  assert.deepEqual(orphanPictures(pictures, [...docs, { f: 'README.md', text: '![b](electron/docs/screens/b.png) ![d](docs/img/d.png)' }]), []);
});

test('scope: pictures under docs/ and electron/docs/; upstream originals left out', () => {
  assert.ok(isDocPicture('electron/docs/screens/start.png'));
  assert.ok(!isDocPicture('assets/hallym/logo/x.png'));
  assert.ok(UPSTREAM.test('vendor/logisim-2.7.1/ORIGIN.md') && UPSTREAM.test('app/src/com/cburch/doc.md'));
  assert.ok(!UPSTREAM.test('docs/PLAN.md'));
});
