/* The registry's shapes as SVG (N-05, D-137): the picture export (SVG, and
   PDF and PNG made from it, N-21) writes exactly what the Canvas draws --
   the same shape list, the same colours (paint.ts color), the same widths
   at 100 %.  Also builds live SVG elements from the same shapes, which is
   how the Canvas-vs-SVG measurement (tools/measure-canvas.ts) drew the
   other way. */

import { color, FONTS, portPx, strokeUnits, wirePx } from './paint.ts';
import { arcEnds, arcSweep, type Box, type Seg, type Shape, type TextShape } from './shapes.ts';
import { type Theme, valueColor, valueKind } from './tokens.ts';

const n = (v: number): string => {
  const r = Math.round(v * 1000) / 1000;
  return Object.is(r, -0) ? '0' : String(r);
};
const esc = (s: string): string => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

// A path's d attribute: arcs become SVG endpoint arcs (a full circle is two halves).
export function pathData(d: Seg[]): string {
  const out: string[] = [];
  let open = false;
  for (const s of d) {
    switch (s[0]) {
      case 'M': out.push(`M${n(s[1])} ${n(s[2])}`); open = true; break;
      case 'L': out.push(`L${n(s[1])} ${n(s[2])}`); break;
      case 'Q': out.push(`Q${n(s[1])} ${n(s[2])} ${n(s[3])} ${n(s[4])}`); break;
      case 'C': out.push(`C${n(s[1])} ${n(s[2])} ${n(s[3])} ${n(s[4])} ${n(s[5])} ${n(s[6])}`); break;
      case 'A': {
        const { start, end } = arcEnds(s);
        const sweep = arcSweep(s);
        out.push(`${open ? 'L' : 'M'}${n(start[0])} ${n(start[1])}`);
        open = true;
        const flag = s[6] ? 0 : 1;
        if (sweep >= Math.PI * 2 - 1e-9) {
          const mid: [number, number] = [2 * s[1] - start[0], 2 * s[2] - start[1]];
          out.push(`A${n(s[3])} ${n(s[3])} 0 0 ${flag} ${n(mid[0])} ${n(mid[1])}`);
          out.push(`A${n(s[3])} ${n(s[3])} 0 0 ${flag} ${n(start[0])} ${n(start[1])}`);
        } else {
          out.push(`A${n(s[3])} ${n(s[3])} 0 ${sweep > Math.PI ? 1 : 0} ${flag} ${n(end[0])} ${n(end[1])}`);
        }
        break;
      }
      case 'Z': out.push('Z'); break;
    }
  }
  return out.join(' ');
}

// minText: text smaller than this (px at `zoom`) is left out, as the Canvas leaves it out (paint.ts MIN_TEXT_PX).
export interface SvgLook { theme: Theme; zoom?: number; fitted?: (t: TextShape) => number; minText?: number }

// The attributes of one shape (tag and attribute pairs), shared by the text writer and the DOM builder.
export function svgParts(s: Shape, look: SvgLook): { tag: string; attrs: [string, string][]; text?: string } | null {
  const zoom = look.zoom ?? 1;
  const paint = (name: 'fill' | 'stroke', p: Shape['fill']): [string, string][] => {
    const c = color(look.theme, p);
    return [[name, c ?? 'none']];
  };
  const stroke = (): [string, string][] => {
    if (!('stroke' in s) || !s.stroke) return [];
    const a: [string, string][] = [...paint('stroke', s.stroke), ['stroke-width', n(strokeUnits(s, zoom))],
      ['stroke-linecap', s.cap ?? 'round'], ['stroke-linejoin', 'round']];
    if (s.dash) a.push(['stroke-dasharray', s.dash.map(n).join(' ')]);
    return a;
  };
  switch (s.k) {
    case 'path':
      return { tag: 'path', attrs: [['d', pathData(s.d)], ...paint('fill', s.fill), ...stroke()] };
    case 'rect':
      return { tag: 'rect', attrs: [['x', n(s.x)], ['y', n(s.y)], ['width', n(s.w)], ['height', n(s.h)], ...(s.r ? [['rx', n(s.r)] as [string, string]] : []), ...paint('fill', s.fill), ...stroke()] };
    case 'ellipse':
      return { tag: 'ellipse', attrs: [['cx', n(s.cx)], ['cy', n(s.cy)], ['rx', n(s.rx)], ['ry', n(s.ry)], ...paint('fill', s.fill), ...stroke()] };
    case 'port': {
      const r = portPx(zoom) / zoom;
      const c = valueKind(s.value) === 'none' ? look.theme.bodyStroke : valueColor(look.theme, s.value);
      return { tag: 'circle', attrs: [['class', 'port'], ['cx', n(s.x)], ['cy', n(s.y)], ['r', n(r)], ['fill', c]] };
    }
    case 'text': {
      const anchor = s.anchor ?? 'start';
      const base = s.baseline ?? 'alphabetic';
      const dom = base === 'middle' ? 'central' : base === 'top' ? 'hanging' : base === 'bottom' ? 'text-after-edge' : 'alphabetic';
      // A text fitted into a width is written at the size the Canvas draws it (look.fitted, paint.ts fittedSize).
      const size = s.fit && look.fitted ? look.fitted(s) : s.size;
      if (look.minText && size * zoom < look.minText) return null;
      const a: [string, string][] = [['x', n(s.x)], ['y', n(s.y)], ['font-family', FONTS[s.font]], ['font-size', n(size)],
        ['text-anchor', anchor], ['dominant-baseline', dom], ['fill', color(look.theme, s.fill ?? 'ink') ?? 'none']];
      if (s.weight && s.weight !== 400) a.push(['font-weight', String(s.weight)]);
      if (s.rotate) a.push(['transform', `rotate(${n((s.rotate * 180) / Math.PI)} ${n(s.x)} ${n(s.y)})`]);
      return { tag: 'text', attrs: a, text: s.text };
    }
  }
}

export function shapeToSvg(s: Shape, look: SvgLook): string {
  const p = svgParts(s, look);
  if (!p) return '';
  const attrs = p.attrs.map(([k, v]) => `${k}="${esc(v)}"`).join(' ');
  return p.text === undefined ? `<${p.tag} ${attrs}/>` : `<${p.tag} ${attrs}>${esc(p.text)}</${p.tag}>`;
}

export const shapesToSvg = (shapes: Shape[], look: SvgLook): string => shapes.map((s) => shapeToSvg(s, look)).join('\n');

// A whole picture: the shapes inside `box` (circuit units, 1 unit = 1 px), on the Canvas's paper.
export function svgDocument(groups: string[], box: Box, look: SvgLook): string {
  const w = box.x1 - box.x0, h = box.y1 - box.y0;
  return [
    `<svg xmlns="http://www.w3.org/2000/svg" width="${n(w)}" height="${n(h)}" viewBox="${n(box.x0)} ${n(box.y0)} ${n(w)} ${n(h)}">`,
    `<rect x="${n(box.x0)}" y="${n(box.y0)}" width="${n(w)}" height="${n(h)}" fill="${look.theme.paper}"/>`,
    ...groups,
    '</svg>',
  ].join('\n');
}

// Live elements (the measurement's SVG drawing).
export function svgElement(doc: Document, s: Shape, look: SvgLook): SVGElement | null {
  const p = svgParts(s, look);
  if (!p) return null;
  const el = doc.createElementNS('http://www.w3.org/2000/svg', p.tag) as SVGElement;
  for (const [k, v] of p.attrs) el.setAttribute(k, v);
  if (p.text !== undefined) el.textContent = p.text;
  return el;
}

// A wire as SVG (the same width rule as the Canvas).
export function wireSvg(a: [number, number], b: [number, number], value: string | undefined, bits: number, look: SvgLook): string {
  const zoom = look.zoom ?? 1;
  const c = valueKind(value) === 'none' ? look.theme.vNone : valueColor(look.theme, value);
  return `<line x1="${n(a[0])}" y1="${n(a[1])}" x2="${n(b[0])}" y2="${n(b[1])}" stroke="${c}" stroke-width="${n(wirePx(zoom, bits) / zoom)}" stroke-linecap="round"/>`;
}
