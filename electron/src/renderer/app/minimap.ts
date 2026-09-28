/* The Minimap (left, lower; v1 Minimap, S-11, E-08, I-176; D-150): the
   whole circuit the Canvas shows, small -- its wires, its parts' outlines,
   tunnels filled in their colours -- and the Canvas's view as a blue
   rectangle.  Press or drag anywhere: that point comes to the middle of the
   Canvas (the zoom stays).  It draws itself again only when the circuit,
   the view or its own size changed, and only while its tab is on show. */

import { sceneTunnelColors } from '../canvas/labels.ts';
import type { CircuitCanvas } from '../canvas/canvas.ts';
import type { Scene } from '../canvas/scene.ts';
import { visible } from '../canvas/view.ts';
import { h } from '../shared/dom.ts';
import type { NoticeHost } from '../shared/notice.ts';
import { type Fit, fitMap, fromMap, toMap, viewRect } from './logic/minimap.ts';

export interface MinimapPanel {
  // Whether its tab is on show (it draws only then).
  show(on: boolean): void;
  // The Canvas shows a circuit, or none (an empty circuit, no file).
  set(scene: Scene | null): void;
  fit(): Fit | null;
}

export const MINIMAP_EMPTY = { title: '회로 전체가 작게 나옵니다', body: 'Canvas에 그린 회로의 전체 모습과 지금 보는 곳이 여기에 나옵니다.' };

export function minimapPanel(o: { host: NoticeHost; board: CircuitCanvas }): MinimapPanel {
  const canvas = h('canvas', { class: 'minimap', 'aria-label': 'Minimap', title: '누르거나 끌면 그 자리가 Canvas 가운데로 옵니다.' }) as HTMLCanvasElement;
  const ctx = canvas.getContext('2d')!;
  const box = h('div', { class: 'minimapbox' }, canvas);
  let scene: Scene | null = null;
  let on = false;
  let raf = 0;
  let last = '';
  let fit: Fit | null = null;
  let dragging = false;

  function frame(): void {
    raf = 0;
    if (!on || !scene) return;
    const r = box.getBoundingClientRect();
    const b = o.board;
    const v = b.view, sz = b.size();
    const key = `${scene.modelVersion}|${scene.circuitId}|${r.width}x${r.height}|${v.x},${v.y},${v.zoom}|${sz.width}x${sz.height}|${devicePixelRatio}`;
    if (key !== last) { last = key; draw(r.width, r.height); }
    raf = requestAnimationFrame(frame);
  }

  // The circuit (wires, parts, tunnels) is drawn once per model and size; the view's rectangle every change.
  let base: { key: string; image: HTMLCanvasElement } | null = null;

  function draw(w: number, hh: number): void {
    const dpr = window.devicePixelRatio || 1;
    const css = getComputedStyle(document.documentElement);
    const color = (name: string, dflt: string) => css.getPropertyValue(name).trim() || dflt;
    const s = scene!;
    const baseKey = `${s.modelVersion}|${s.circuitId}|${w}x${hh}|${dpr}`;
    if (!base || base.key !== baseKey) base = { key: baseKey, image: drawBase(s, w, hh, dpr, color) };
    canvas.width = Math.max(1, Math.round(w * dpr));
    canvas.height = Math.max(1, Math.round(hh * dpr));
    canvas.style.width = `${w}px`;
    canvas.style.height = `${hh}px`;
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.drawImage(base.image, 0, 0);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    // the Canvas's view
    const sz = o.board.size();
    if (fit && sz.width > 0) {
      const r = viewRect(fit, visible(o.board.view, sz.width, sz.height));
      ctx.fillStyle = 'rgba(0,85,165,0.06)';
      ctx.fillRect(r.x, r.y, r.w, r.h);
      ctx.strokeStyle = color('--blue', '#0055a5');
      ctx.lineWidth = 1.5;
      ctx.strokeRect(r.x, r.y, r.w, r.h);
    }
  }

  function drawBase(s: Scene, w: number, hh: number, dpr: number, color: (name: string, dflt: string) => string): HTMLCanvasElement {
    const img = document.createElement('canvas');
    img.width = Math.max(1, Math.round(w * dpr));
    img.height = Math.max(1, Math.round(hh * dpr));
    const g = img.getContext('2d')!;
    g.setTransform(dpr, 0, 0, dpr, 0, 0);
    g.fillStyle = color('--white', '#ffffff');
    g.fillRect(0, 0, w, hh);
    fit = fitMap(s.extent(), w, hh);
    const f = fit;
    // wires
    g.strokeStyle = color('--muted', '#65707e');
    g.lineWidth = 1;
    g.beginPath();
    for (const wire of s.wires.values()) {
      const a = toMap(f, wire.a), b = toMap(f, wire.b);
      g.moveTo(Math.round(a[0]) + 0.5, Math.round(a[1]) + 0.5);
      g.lineTo(Math.round(b[0]) + 0.5, Math.round(b[1]) + 0.5);
    }
    g.stroke();
    // parts: outlines; tunnels filled in their colours (v1)
    const tunnels = sceneTunnelColors(s);
    g.strokeStyle = color('--text', '#1f2933');
    for (const c of s.components.values()) {
      const [x, y, bw, bh] = c.bounds;
      const [mx, my] = toMap(f, [x, y]);
      const rw = Math.max(2, Math.round(bw * f.scale)), rh = Math.max(2, Math.round(bh * f.scale));
      const t = c.lib === 'Wiring' && c.name === 'Tunnel' ? tunnels.get(c.attrs.label ?? '') : undefined;
      if (t) { g.fillStyle = t; g.fillRect(Math.round(mx), Math.round(my), rw, rh); }
      else g.strokeRect(Math.round(mx) + 0.5, Math.round(my) + 0.5, rw, rh);
    }
    return img;
  }

  function centerAt(e: PointerEvent): void {
    if (!fit) return;
    const r = canvas.getBoundingClientRect();
    o.board.centerOn(fromMap(fit, [e.clientX - r.left, e.clientY - r.top]));
  }
  canvas.addEventListener('pointerdown', (e) => {
    dragging = true;
    canvas.setPointerCapture(e.pointerId);
    centerAt(e);
  });
  canvas.addEventListener('pointermove', (e) => { if (dragging) centerAt(e); });
  const end = (e: PointerEvent) => {
    dragging = false;
    if (canvas.hasPointerCapture(e.pointerId)) canvas.releasePointerCapture(e.pointerId);
  };
  canvas.addEventListener('pointerup', end);
  canvas.addEventListener('pointercancel', end);

  function render(): void {
    if (!scene) { o.host.empty(MINIMAP_EMPTY); return; }
    if (o.host.root.firstChild !== box) o.host.fill(box);
    last = '';
    if (on && !raf) raf = requestAnimationFrame(frame);
  }

  return {
    show: (next) => { on = next; if (on) render(); },
    set: (next) => { scene = next; render(); },
    fit: () => fit,
  };
}
