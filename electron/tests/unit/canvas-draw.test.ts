/* The drawing language and its two writers (N-05, D-137): shapes.ts (turning,
   outlines, boxes, distances), paint.ts (Canvas 2D: widths, colours, text)
   and svg.ts (picture export) -- one list of shapes, the same geometry and
   colours both ways. */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';

import type { Component } from '../../src/main/protocol.ts';
import { color, type Ctx2D, fittedSize, MIN_TEXT_PX, paintShape, paintShapes, portPx, wirePx } from '../../src/renderer/canvas/paint.ts';
import { NO_STATE } from '../../src/renderer/canvas/parts/common.ts';
import { rendererFor } from '../../src/renderer/canvas/registry.ts';
import { apply, distanceTo, outline, Path, path as pathShape, type Shape, shapeBox, turn, turned } from '../../src/renderer/canvas/shapes.ts';
import { pathData, shapesToSvg, shapeToSvg, svgDocument, svgParts } from '../../src/renderer/canvas/svg.ts';
import { THEME } from '../../src/renderer/canvas/tokens.ts';

const cases = (JSON.parse(readFileSync(path.join(import.meta.dirname, '../fixtures/geometry.json'), 'utf8')) as { cases: { case: string; component: Component }[] }).cases;

// A Canvas 2D context that writes down what it is told.
export function recorder(): Ctx2D & { log: string[] } {
  const log: string[] = [];
  const state: Record<string, unknown> = {};
  const rec = (name: string) => (...a: unknown[]) => { log.push(`${name}(${a.map((x) => (typeof x === 'number' ? Math.round(x * 1000) / 1000 : String(x))).join(',')})`); };
  const target = { log, measureText: (t: string) => ({ width: t.length * 56 }) } as unknown as Ctx2D & { log: string[] };
  return new Proxy(target, {
    get: (t, k: string) => (k in t ? (t as unknown as Record<string, unknown>)[k] : k in state ? state[k] : rec(k)),
    set: (_t, k: string, v) => { state[k] = v; log.push(`${k}=${v}`); return true; },
  });
}

test('turning a drawing to its facing: north a quarter turn anticlockwise, west a half, south clockwise', () => {
  assert.deepEqual(apply(turn('east', 100, 50), -30, 10), [70, 60]);
  assert.deepEqual(apply(turn('north', 100, 50), -30, 10), [110, 80]);
  assert.deepEqual(apply(turn('west', 100, 50), -30, 10), [130, 40]);
  assert.deepEqual(apply(turn('south', 100, 50), -30, 10), [90, 20]);
  const r = turned(turn('north', 0, 0), { k: 'rect', x: -40, y: -10, w: 40, h: 20 });
  assert.deepEqual([r.k === 'rect' && r.x, r.k === 'rect' && r.y, r.k === 'rect' && r.w, r.k === 'rect' && r.h], [-10, 0, 20, 40]);
  // an arc turns with the drawing (its angles), keeping its direction
  const a = turned(turn('south', 0, 0), pathShape(new Path().A(0, 0, 10, 0, Math.PI / 2)));
  assert.ok(a.k === 'path' && a.d[0][0] === 'A' && Math.abs((a.d[0][4] as number) - Math.PI / 2) < 1e-9);
});

test('outlines and boxes: rounded corners are round, text is measured, turned text turns its box', () => {
  const sharp = outline({ k: 'rect', x: 0, y: 0, w: 20, h: 10 })[0];
  assert.equal(sharp.length, 5);
  const round = { k: 'rect' as const, x: 0, y: 0, w: 20, h: 10, r: 4 };
  assert.ok(distanceTo([0, 0], round) > 1.5, 'the corner point is off a rounded corner');
  assert.ok(distanceTo([10, 0], round) < 1e-9);
  const t = { k: 'text' as const, x: 0, y: 0, text: 'abcd', font: 'code' as const, size: 10, anchor: 'start' as const, baseline: 'middle' as const };
  assert.deepEqual(shapeBox(t), { x0: 0, y0: -5, x1: 20, y1: 5 });
  const up = shapeBox({ ...t, rotate: -Math.PI / 2 });
  assert.ok(Math.abs(up.y0 + 20) < 1e-9 && Math.abs(up.x1 - 5) < 1e-9, JSON.stringify(up));
  assert.deepEqual(shapeBox({ ...t, fit: 12 }), { x0: 0, y0: -5, x1: 12, y1: 5 });
});

test('widths on screen: wires and buses at 25, 100 and 400 % (v1 S-13), port marks', () => {
  assert.deepEqual([0.25, 1, 4].map((z) => wirePx(z, 1)), [1.25, 2, 4]);
  assert.deepEqual([0.25, 1, 4].map((z) => wirePx(z, 32)), [2.25, 3.5, 7]);
  assert.ok(wirePx(1, 32) > wirePx(1, 1), 'a bus is wider');
  assert.equal(portPx(0.25), 1.5);
  assert.equal(portPx(4), 3.5);
});

test('colours: tokens, values, literals', () => {
  assert.equal(color(THEME, 'navy'), '#00205b');
  assert.equal(color(THEME, { value: '1' }), THEME.vOne);
  assert.equal(color(THEME, { value: '0' }), THEME.vZero);
  assert.equal(color(THEME, { value: 'x' }), THEME.vFloat);
  assert.equal(color(THEME, { value: '01E1' }), THEME.vError);
  assert.equal(color(THEME, { value: '0101' }), THEME.vBus);
  assert.equal(color(THEME, { value: 'xxxx' }), THEME.vFloat);
  assert.equal(color(THEME, { rgb: '#ff0000', alpha: 0.5 }), 'rgba(255,0,0,0.5)');
  assert.equal(color(THEME, 'none'), null);
  assert.equal(color(THEME, '#123456'), '#123456');
});

test('the Canvas painter: paths, rounded boxes, stubs as wide as wires, text too small to read left out', () => {
  const ctx = recorder();
  paintShape(ctx, { k: 'rect', x: 1, y: 2, w: 30, h: 20, r: 3, fill: 'body', stroke: 'bodyStroke', width: 1.6 }, { theme: THEME, zoom: 1 });
  assert.ok(ctx.log.includes('roundRect(1,2,30,20,3)'));
  assert.ok(ctx.log.includes('fillStyle=#ffffff') && ctx.log.includes('strokeStyle=#2b3743') && ctx.log.includes('lineWidth=1.6'));
  const stub = recorder();
  paintShape(stub, { k: 'path', role: 'stub', d: [['M', 0, 0], ['L', 10, 0]], stroke: { value: '1' }, bits: 1 }, { theme: THEME, zoom: 4 });
  assert.ok(stub.log.includes(`lineWidth=${wirePx(4, 1) / 4}`), stub.log.join(' '));
  const small = recorder();
  paintShape(small, { k: 'text', x: 0, y: 0, text: 'tiny', font: 'ui', size: 10 }, { theme: THEME, zoom: 0.25 });
  assert.ok(!small.log.some((l) => l.startsWith('fillText')), 'unreadable text is not drawn');
  const big = recorder();
  paintShape(big, { k: 'text', x: 0, y: 0, text: 'fits', font: 'ui', size: 10, fit: 30 }, { theme: THEME, zoom: 1 });
  assert.ok(big.log.some((l) => l.startsWith('fillText(fits')));
  // the readable minimum (MIN_TEXT_PX, D-137): 8 px drawn, 6.5 px not; a fitted text shrunk under it is not drawn either
  const at = (size: number, zoom: number, fit?: number) => { const r = recorder(); paintShape(r, { k: 'text', x: 0, y: 0, text: 'reg', font: 'ui', size, fit }, { theme: THEME, zoom }); return r.log.some((l) => l.startsWith('fillText')); };
  assert.equal(MIN_TEXT_PX, 8);
  assert.deepEqual([at(8, 1), at(6.5, 1), at(6.5, 1.5), at(10, 1, 9)], [true, false, true, false]);
  assert.ok(fittedSize(recorder(), { k: 'text', x: 0, y: 0, text: 'a longer text', font: 'ui', size: 10, fit: 20 }) < 10);
});

test('SVG: arcs as endpoint arcs (a full circle in two halves), the same colours and widths as the Canvas', () => {
  assert.equal(pathData([['M', 0, 0], ['L', 10, 0], ['Q', 15, 5, 20, 0], ['Z']]), 'M0 0 L10 0 Q15 5 20 0 Z');
  assert.equal(pathData([['A', 0, 0, 10, 0, Math.PI, false]]), 'M10 0 A10 10 0 0 1 -10 0');
  assert.equal(pathData([['A', 0, 0, 10, Math.PI / 2, -Math.PI / 2, true]]), 'M0 10 A10 10 0 0 0 0 -10');
  assert.equal(pathData([['A', 5, 5, 5, 0, Math.PI * 2, false]]), 'M10 5 A5 5 0 0 1 0 5 A5 5 0 0 1 10 5');
  const rect = shapeToSvg({ k: 'rect', x: 1, y: 2, w: 30, h: 20, r: 3, fill: 'body', stroke: 'bodyStroke', width: 1.6 }, { theme: THEME });
  assert.equal(rect, '<rect x="1" y="2" width="30" height="20" rx="3" fill="#ffffff" stroke="#2b3743" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>');
  const stub = svgParts({ k: 'path', role: 'stub', d: [['M', 0, 0], ['L', 10, 0]], stroke: { value: '0101' }, bits: 4 }, { theme: THEME, zoom: 1 })!;
  assert.deepEqual(stub.attrs.find(([k]) => k === 'stroke'), ['stroke', THEME.vBus]);
  assert.deepEqual(stub.attrs.find(([k]) => k === 'stroke-width'), ['stroke-width', String(wirePx(1, 4))]);
  const t = shapeToSvg({ k: 'text', x: 5, y: 6, text: 'a<b', font: 'code', size: 10, weight: 700, anchor: 'middle', baseline: 'middle', rotate: -Math.PI / 2 }, { theme: THEME });
  assert.ok(t.includes('font-family="D2Coding, monospace"') && t.includes('dominant-baseline="central"') && t.includes('>a&lt;b</text>') && t.includes('transform="rotate(-90 5 6)"'), t);
  const fitted = shapeToSvg({ k: 'text', x: 0, y: 0, text: 'long', font: 'ui', size: 10, fit: 5 }, { theme: THEME, fitted: () => 6.5 });
  assert.ok(fitted.includes('font-size="6.5"'), 'a fitted text is written at the size the Canvas draws it');
  const doc = svgDocument(['<g/>'], { x0: 0, y0: 0, x1: 100, y1: 50 }, { theme: THEME });
  assert.ok(doc.startsWith('<svg xmlns="http://www.w3.org/2000/svg" width="100" height="50" viewBox="0 0 100 50">'));
});

/* Export cannot drift from the screen: for every engine-made case, the SVG
   has an element for every shape the Canvas paints, at the same points,
   in the same colours. */
test(`the same drawing both ways: ${cases.length} cases, every shape's geometry and colour in the SVG as on the Canvas`, () => {
  let shapes = 0;
  for (const { case: name, component } of cases) {
    const list: Shape[] = rendererFor(component).draw(component, NO_STATE);
    const svg = shapesToSvg(list, { theme: THEME, zoom: 1 }).split('\n').filter(Boolean);
    assert.equal(svg.length, list.length, name);
    list.forEach((s, i) => {
      shapes++;
      const el = svg[i];
      if (s.k === 'port') assert.ok(el.includes(`cx="${s.x}"`) && el.includes(`cy="${s.y}"`), `${name}: port ${s.i}: ${el}`);
      if (s.k === 'rect') assert.ok(el.startsWith('<rect') && el.includes(`width="${Math.round(s.w * 1000) / 1000}"`), `${name}: ${el}`);
      if (s.k === 'text') assert.ok(el.includes(`>${s.text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')}</text>`), `${name}: ${el}`);
      if ('fill' in s && s.fill && s.k !== 'port' && s.k !== 'text') assert.ok(el.includes(`fill="${color(THEME, s.fill)}"`), `${name}: fill ${el}`);
      if ('stroke' in s && s.stroke && s.k !== 'text') assert.ok(el.includes(`stroke="${color(THEME, s.stroke)}"`), `${name}: stroke ${el}`);
    });
    // the Canvas paints them all without failing
    paintShapes(recorder(), list, { theme: THEME, zoom: 1 });
  }
  assert.ok(shapes > 5000, `${shapes} shapes`);
});
