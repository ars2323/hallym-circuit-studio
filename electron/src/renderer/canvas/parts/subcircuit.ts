/* Subcircuit instances (N-05): the circuit's appearance as the engine sends
   it (docs/engine-api.md Appearance) -- the original's default box with its
   notch, or the student's own drawing -- placed like Logisim places it
   (CircuitAppearance.paintSubcircuit): a point p of the appearance goes to
   loc + R(θ)(p − anchor), θ = the appearance's facing − the instance's.
   The appearance's own port marks are placed the same way; the geometry
   check (N-06) holds them on the engine's ports. */

import type { Appearance, AppearanceShape } from '../../../main/protocol.ts';
import { Path, path, type Point, type Shape, turnAll, type Turn } from '../shapes.ts';
import type { Paint } from '../shapes.ts';
import { parseFont } from './base.ts';
import { type Part, type PartState, text } from './common.ts';

const RAD: Record<string, number> = { east: 0, north: Math.PI / 2, west: Math.PI, south: -Math.PI / 2 };

export function placement(p: Part, app: Appearance): Turn {
  const theta = (RAD[app.facing] ?? 0) - (RAD[p.facing ?? 'east'] ?? 0);
  const cos = Math.round(Math.cos(theta)), sin = Math.round(Math.sin(theta));
  const [ax, ay] = app.anchor;
  return { cos, sin, ox: p.loc[0] - (ax * cos - ay * sin), oy: p.loc[1] - (ax * sin + ay * cos) };
}

const nums = (s: string | undefined): number[] => (s ?? '').split(/[\s,]+/).filter(Boolean).map(Number);
const f = (a: Record<string, string>, k: string, d = 0) => (a[k] === undefined ? d : Number(a[k]));

function paintOf(c: string | undefined, fallback: Paint | undefined): Paint | undefined {
  if (c === undefined) return fallback;
  if (c === 'none') return undefined;
  if (/^#000000$/i.test(c)) return 'bodyStroke';
  if (/^#808080$/i.test(c)) return 'dim';
  if (/^#ffffff$/i.test(c)) return 'body';
  return { rgb: c };
}

// One appearance element as shapes of our language, in the appearance's coordinates.
export function fromSvg(e: AppearanceShape, first: boolean): Shape[] {
  const a = e.attrs;
  const stroke = paintOf(a.stroke, 'bodyStroke');
  const width = Math.max(1.2, f(a, 'stroke-width', 1) * 0.8);
  // The bottom-most closed shape is the body: filled white where the original leaves it empty.
  const fill = paintOf(a.fill, e.tag === 'text' ? 'ink' : undefined) ?? (first ? 'body' : undefined);
  switch (e.tag) {
    case 'rect': {
      const r = f(a, 'rx', 0);
      return [{ k: 'rect', role: 'body', x: f(a, 'x'), y: f(a, 'y'), w: f(a, 'width'), h: f(a, 'height'), r: r || 3, fill, stroke, width }];
    }
    case 'ellipse':
      return [{ k: 'ellipse', role: 'body', cx: f(a, 'cx'), cy: f(a, 'cy'), rx: f(a, 'rx'), ry: f(a, 'ry'), fill, stroke, width }];
    case 'line':
      return [path(new Path().M(f(a, 'x1'), f(a, 'y1')).L(f(a, 'x2'), f(a, 'y2')), { role: 'body', stroke, width })];
    case 'polyline':
    case 'polygon': {
      const v = nums(a.points);
      const pts: Point[] = [];
      for (let i = 0; i + 1 < v.length; i += 2) pts.push([v[i], v[i + 1]]);
      return [path(new Path().poly(pts, e.tag === 'polygon'), { role: 'body', stroke, width, fill: e.tag === 'polygon' ? fill : undefined })];
    }
    case 'path': {
      // Logisim's curves: M x,y Q cx,cy x,y
      const v = nums((a.d ?? '').replace(/[MQ]/g, ' '));
      if (v.length < 6) return [];
      return [path(new Path().M(v[0], v[1]).Q(v[2], v[3], v[4], v[5]), { role: 'body', stroke, width, fill: paintOf(a.fill, undefined) })];
    }
    case 'text': {
      const font = parseFont(`${a['font-family'] ?? 'SansSerif'} ${a['font-weight'] === 'bold' ? 'bold' : 'plain'} ${a['font-size'] ?? '12'}`);
      return [text(f(a, 'x'), f(a, 'y'), e.text ?? '', { role: 'deco', font: font.font, size: font.size, weight: font.weight,
        anchor: (a['text-anchor'] as 'start' | 'middle' | 'end') ?? 'middle', baseline: 'alphabetic', fill: fill ?? 'ink' })];
    }
    default:
      return [];
  }
}

export function drawSubcircuit(p: Part, st: PartState): Shape[] {
  const app = p.appearance;
  if (!app) return [];
  const t = placement(p, app);
  const shapes: Shape[] = [];
  app.shapes.forEach((e, k) => shapes.push(...fromSvg(e, k === 0 || (app.default && e.tag === 'rect'))));
  const placed = turnAll(t, shapes).map((s) => {
    if (s.k !== 'text' || (t.cos === 1 && t.sin === 0)) return s;
    // Turned half round the text stays upright, mirrored in place (start and end swap, the line
    // moves down by its height) so it covers what it covered; a quarter turn turns it, as the original does.
    if (t.cos === -1) {
      return { ...s, y: s.y + s.size * 0.6, anchor: s.anchor === 'start' ? 'end' as const : s.anchor === 'end' ? 'start' as const : s.anchor };
    }
    return { ...s, rotate: Math.atan2(t.sin, t.cos) };
  });
  // Ports: the appearance's, placed; the engine's index where one is at the same point.
  for (const q of app.ports) {
    const [x, y] = [t.ox + q.at[0] * t.cos - q.at[1] * t.sin, t.oy + q.at[0] * t.sin + q.at[1] * t.cos];
    const e = p.ports.find((pp) => pp.loc[0] === x && pp.loc[1] === y);
    placed.push({ k: 'port', role: 'port', x, y, i: e ? e.i : -1, dir: e ? e.dir : q.input ? 'in' : 'out', bits: e ? e.width : 1, value: e ? st.value(e.i) : undefined });
  }
  // The circuit's own label (Circuit Label attribute), in the middle.
  if (app.label?.text) {
    const [x, y, w, h] = p.bounds;
    const font = parseFont(app.label.font);
    placed.push(text(x + w / 2, y + h / 2, app.label.text.replace(/\\n/g, ' '), { font: font.font, size: font.size, weight: font.weight, fit: Math.max(10, Math.min(w, h) - 6) }));
  }
  return placed;
}
