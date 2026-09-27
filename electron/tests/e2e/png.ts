/* Enough of PNG to compare two screenshots pixel by pixel in the tests:
   8-bit RGB or RGBA, not interlaced (what Chromium's captures are). */

import { inflateSync } from 'node:zlib';

export interface Pixels { width: number; height: number; rgba: Uint8Array }

export function decodePng(png: Buffer): Pixels {
  let at = 8;
  let width = 0;
  let height = 0;
  let channels = 0;
  const idat: Buffer[] = [];
  while (at < png.length) {
    const len = png.readUInt32BE(at);
    const type = png.toString('latin1', at + 4, at + 8);
    const body = png.subarray(at + 8, at + 8 + len);
    if (type === 'IHDR') {
      width = body.readUInt32BE(0);
      height = body.readUInt32BE(4);
      const [depth, color, , , interlace] = body.subarray(8, 13);
      if (depth !== 8 || interlace !== 0 || (color !== 2 && color !== 6)) throw new Error(`PNG: depth ${depth}, colour ${color}, interlace ${interlace}`);
      channels = color === 6 ? 4 : 3;
    } else if (type === 'IDAT') idat.push(body);
    at += 12 + len;
  }
  const raw = inflateSync(Buffer.concat(idat));
  const stride = width * channels;
  const out = new Uint8Array(width * height * 4);
  const prev = new Uint8Array(stride);
  const line = new Uint8Array(stride);
  for (let y = 0; y < height; y += 1) {
    const filter = raw[y * (stride + 1)];
    const src = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1));
    for (let i = 0; i < stride; i += 1) {
      const a = i >= channels ? line[i - channels] : 0;
      const b = prev[i];
      const c = i >= channels ? prev[i - channels] : 0;
      const p = a + b - c;
      const pa = Math.abs(p - a);
      const pb = Math.abs(p - b);
      const pc = Math.abs(p - c);
      const paeth = pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
      const add = [0, a, b, (a + b) >> 1, paeth][filter];
      line[i] = (src[i] + add) & 0xff;
    }
    for (let x = 0; x < width; x += 1) {
      for (let k = 0; k < 4; k += 1) out[(y * width + x) * 4 + k] = k < channels ? line[x * channels + k] : 255;
    }
    prev.set(line);
  }
  return { width, height, rgba: out };
}

export interface Rect { x: number; y: number; width: number; height: number }

// Where two screenshots differ outside `ignore` (CSS px, device scale 1): '' when
// they do not, else the box around the pixels that do.
export function pixelDiff(a: Buffer, b: Buffer, ignore: Rect[] = []): string {
  const p = decodePng(a);
  const q = decodePng(b);
  if (p.width !== q.width || p.height !== q.height) return `sizes ${p.width}x${p.height} and ${q.width}x${q.height}`;
  let [l, t, r, bt, n] = [Infinity, Infinity, -1, -1, 0];
  for (let i = 0; i < p.rgba.length; i += 4) {
    if (p.rgba[i] === q.rgba[i] && p.rgba[i + 1] === q.rgba[i + 1] && p.rgba[i + 2] === q.rgba[i + 2] && p.rgba[i + 3] === q.rgba[i + 3]) continue;
    const x = (i / 4) % p.width;
    const y = Math.floor(i / 4 / p.width);
    if (ignore.some((g) => x >= Math.floor(g.x) && x < Math.ceil(g.x + g.width) && y >= Math.floor(g.y) && y < Math.ceil(g.y + g.height))) continue;
    [l, t, r, bt, n] = [Math.min(l, x), Math.min(t, y), Math.max(r, x), Math.max(bt, y), n + 1];
  }
  return n === 0 ? '' : `${n} pixels differ within x ${l}..${r}, y ${t}..${bt}`;
}
