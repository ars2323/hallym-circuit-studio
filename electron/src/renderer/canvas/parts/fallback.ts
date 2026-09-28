/* Any other part (docs/canvas-renderers.md lists the ones we know of: a
   transistor, a joystick, a keyboard, a TTY … and parts of other JAR
   libraries): the original's look reduced to what always holds -- a box
   the size of its bounds, its name in it, its ports. */

import type { Shape } from '../shapes.ts';
import { boundsBox, type Part, type PartState, portMarks, text } from './common.ts';

export function drawFallback(p: Part, st: PartState): Shape[] {
  const [x, y, w, h] = p.bounds;
  if (w <= 0 || h <= 0) return portMarks(p, st);
  // a port on a corner (a transistor's): the outline on the bounds themselves, square, so it reaches the port
  const corner = p.ports.some((q) => (q.loc[0] === x || q.loc[0] === x + w) && (q.loc[1] === y || q.loc[1] === y + h));
  return [
    corner ? { k: 'rect', role: 'body', x, y, w, h, fill: 'bodySoft', stroke: 'bodyStroke', width: 1.6 } : boundsBox(p, 3, 'bodySoft'),
    text(x + w / 2, y + h / 2 + 0.5, p.name, { size: Math.min(9, h * 0.45), weight: 600, fill: 'ink2', fit: Math.max(6, w - 6) }),
    ...portMarks(p, st),
  ];
}
